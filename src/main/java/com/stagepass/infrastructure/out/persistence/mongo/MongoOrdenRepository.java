package com.stagepass.infrastructure.out.persistence.mongo;

import com.stagepass.domain.model.OrdenCompra;
import com.stagepass.ports.out.OrdenRepository;
import com.stagepass.infrastructure.out.persistence.InstantTexto;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

/**
 * Adaptador secundario de resenas y ordenes de compra sobre MongoDB. Guarda documentos
 * propios y devuelve entidades del dominio; los estados activos y los vencidos son las
 * dos consultas calientes y llevan indice desde el arranque.
 */
public class MongoOrdenRepository implements OrdenRepository {

    private static final String COLECCION = "ordenes";
    private static final List<String> ESTADOS_ACTIVOS = List.of("PENDIENTE_PAGO", "APROBADA");

    private final MongoTemplate mongo;

    public MongoOrdenRepository(MongoTemplate mongo) {
        this.mongo = mongo;
        prepararIndices();
    }

    @Override
    public void guardar(OrdenCompra orden) {
        // save es upsert por id: reconfirmar una orden (reintento del webhook) no duplica.
        mongo.save(MapeadorDeOrden.aDocumento(orden), COLECCION);
    }

    @Override
    public OrdenCompra buscarPorId(String ordenId) {
        OrdenDocumento documento = mongo.findById(ordenId, OrdenDocumento.class, COLECCION);
        return documento == null ? null : MapeadorDeOrden.aEntidad(documento);
    }

    @Override
    public List<OrdenCompra> buscarActivasPorClienteYEvento(String clienteId, String eventoId) {
        // Activas para el tope de 6 boletas: pendientes de pago mas aprobadas.
        Criteria activas = Criteria.where("cliente.id").is(clienteId)
                .and("eventoId").is(eventoId)
                .and("estado").in(ESTADOS_ACTIVOS);
        return mongo.find(new Query(activas), OrdenDocumento.class, COLECCION).stream()
                .map(MapeadorDeOrden::aEntidad)
                .toList();
    }

    @Override
    public List<OrdenCompra> buscarVencidas(Instant ahora) {
        // Solo pendientes: una orden ya pagada no expira aunque su ventana haya pasado.
        Criteria vencidas = Criteria.where("estado").is("PENDIENTE_PAGO")
                .and("expiraEn").lt(InstantTexto.formatear(ahora));
        return mongo.find(new Query(vencidas), OrdenDocumento.class, COLECCION).stream()
                .map(MapeadorDeOrden::aEntidad)
                .toList();
    }

    private void prepararIndices() {
        // Idempotente: crear un indice existente es un no-op en el arranque del adaptador.
        mongo.indexOps(COLECCION).ensureIndex(new Index()
                .on("cliente.id", Sort.Direction.ASC)
                .on("eventoId", Sort.Direction.ASC)
                .on("estado", Sort.Direction.ASC));
        mongo.indexOps(COLECCION).ensureIndex(new Index()
                .on("estado", Sort.Direction.ASC)
                .on("expiraEn", Sort.Direction.ASC));
    }
}
