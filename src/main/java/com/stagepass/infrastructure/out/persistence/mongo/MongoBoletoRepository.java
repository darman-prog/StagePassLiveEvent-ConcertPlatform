package com.stagepass.infrastructure.out.persistence.mongo;

import com.stagepass.domain.model.Boleto;
import com.stagepass.ports.out.BoletoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Adaptador secundario de boletas emitidas sobre MongoDB. El codigo de redencion es la
 * llave de busqueda en puerta, con indice unico para bloquear duplicados.
 */
public class MongoBoletoRepository implements BoletoRepository {

    private static final String COLECCION = "boletos";

    private final MongoTemplate mongo;

    public MongoBoletoRepository(MongoTemplate mongo) {
        this.mongo = mongo;
        prepararIndice();
    }

    @Override
    public void guardar(Boleto boleto) {
        mongo.save(MapeadorDeBoleto.aDocumento(boleto), COLECCION);
    }

    @Override
    public Boleto buscarPorCodigo(String codigoRedencion) {
        Criteria porCodigo = Criteria.where("codigoRedencion").is(codigoRedencion);
        BoletoDocumento documento = mongo.findOne(new Query(porCodigo), BoletoDocumento.class, COLECCION);
        return documento == null ? null : MapeadorDeBoleto.aEntidad(documento);
    }

    private void prepararIndice() {
        mongo.indexOps(COLECCION).ensureIndex(
                new Index().on("codigoRedencion", Sort.Direction.ASC).unique());
    }
}
