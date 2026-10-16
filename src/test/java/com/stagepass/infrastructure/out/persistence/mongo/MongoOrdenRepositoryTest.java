package com.stagepass.infrastructure.out.persistence.mongo;

import com.stagepass.domain.model.Asiento;
import com.stagepass.domain.model.Cliente;
import com.stagepass.domain.model.EstadoOrden;
import com.stagepass.domain.model.OrdenCompra;
import com.stagepass.domain.service.GeneradorFirma;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integracion del adaptador Mongo contra un contenedor real. El caso critico es el
 * round-trip: si la relectura pierde un solo nano del vencimiento, la firma del boleto
 * ya no valida y la puerta rechazaria un boleto legítimo.
 */
class MongoOrdenRepositoryTest {

    private static final Instant EXPIRA_EN = Instant.parse("2026-10-16T08:10:00.123456789Z");

    private MongoTemplate mongo;
    private MongoOrdenRepository repo;

    @BeforeEach
    void prepararColeccion() {
        mongo = MongoDePrueba.plantilla();
        // Limpiar y reconstruir el repositorio por test: los indices vuelven a crearse.
        mongo.dropCollection("ordenes");
        repo = new MongoOrdenRepository(mongo);
    }

    private OrdenCompra ordenDeReferencia(String id) {
        Cliente cliente = new Cliente("CL-001", "Camila Osorio", "camila.osorio@example.com", "573001234567", "CO");
        Asiento vip = new Asiento("VIP-A1", "REC-01", "VIP-A1", new BigDecimal("150000.00"));
        Asiento general = new Asiento("GEN-01", "REC-01", "General-01", new BigDecimal("80000.00"));
        vip.bloquear(EXPIRA_EN);

        return new OrdenCompra(id, "EV-001", cliente, List.of(vip, general),
                new BigDecimal("23000.00"), new BigDecimal("0.00"), new BigDecimal("253000.00"), EXPIRA_EN);
    }

    @Test
    void guardarYReleerPreservaDatosExactosYLaFirmaDelBoleto() {
        OrdenCompra original = ordenDeReferencia("ORD-001");
        String firmaOriginal = new GeneradorFirma().generar(original, "clave-secreta");

        repo.guardar(original);
        OrdenCompra releida = repo.buscarPorId("ORD-001");

        assertThat(releida).isNotNull();
        assertThat(releida.getEventoId()).isEqualTo("EV-001");
        assertThat(releida.getExpiraEn()).isEqualTo(EXPIRA_EN);
        assertThat(releida.getMontoTotal()).isEqualByComparingTo("253000.00");
        assertThat(releida.getEstado()).isEqualTo(EstadoOrden.PENDIENTE_PAGO);
        assertThat(releida.getCliente().getCorreoElectronico()).isEqualTo("camila.osorio@example.com");
        assertThat(releida.getAsientos()).hasSize(2);
        assertThat(releida.getAsientos().getFirst().getEstado()).isEqualTo(
                com.stagepass.domain.model.EstadoAsiento.BLOQUEADO);
        assertThat(releida.getAsientos().getFirst().getBloqueadoHasta()).isEqualTo(EXPIRA_EN);

        // El corazon del round-trip: la firma calculada sobre lo releido es la misma.
        assertThat(new GeneradorFirma().generar(releida, "clave-secreta")).isEqualTo(firmaOriginal);
    }

    @Test
    void guardarDosVecesElMismoIdNoDuplicaLaOrden() {
        repo.guardar(ordenDeReferencia("ORD-002"));
        repo.guardar(ordenDeReferencia("ORD-002"));

        Long total = mongo.getCollection("ordenes").countDocuments();
        assertThat(total).isEqualTo(1L);
    }

    @Test
    void buscarPorIdDevuelveNullCuandoLaOrdenNoExiste() {
        assertThat(repo.buscarPorId("ORD-999")).isNull();
    }

    @Test
    void buscarActivasPorClienteYEventoExcluyeRechazadasYExpiradas() {
        repo.guardar(ordenDeReferencia("ORD-010"));
        OrdenCompra aprobada = ordenDeReferencia("ORD-011");
        aprobada.aprobar();
        repo.guardar(aprobada);
        OrdenCompra rechazada = ordenDeReferencia("ORD-012");
        rechazada.rechazar();
        repo.guardar(rechazada);
        OrdenCompra expirada = ordenDeReferencia("ORD-013");
        expirada.expirar();
        repo.guardar(expirada);

        List<OrdenCompra> activas = repo.buscarActivasPorClienteYEvento("CL-001", "EV-001");

        // Para el tope de 6 boletas solo cuentan pendiente de pago y aprobada.
        assertThat(activas).extracting(OrdenCompra::getId)
                .containsExactlyInAnyOrder("ORD-010", "ORD-011");
    }

    @Test
    void buscarVencidasDevuelveSoloPendientesConLaVentanaPasada() {
        OrdenCompra vencida = new OrdenCompra("ORD-020", "EV-001",
                new Cliente("CL-002", "Andres Villalba", "andres.villalba@example.com", "573007654321", "CO"),
                List.of(new Asiento("BOX-01", "REC-02", "Palco-01", new BigDecimal("220000.00"))),
                new BigDecimal("22000.00"), new BigDecimal("0.00"), new BigDecimal("242000.00"),
                Instant.parse("2026-10-16T07:59:00.000000000Z"));
        repo.guardar(vencida);

        OrdenCompra vigente = ordenDeReferencia("ORD-021");
        repo.guardar(vigente);

        OrdenCompra pagada = ordenDeReferencia("ORD-022");
        pagada.aprobar();
        repo.guardar(pagada);

        List<OrdenCompra> vencidas = repo.buscarVencidas(Instant.parse("2026-10-16T08:00:00.000000000Z"));

        assertThat(vencidas).extracting(OrdenCompra::getId).containsExactly("ORD-020");
    }
}
