package com.stagepass.infrastructure;

import com.stagepass.ports.in.MantenimientoUseCase;
import com.stagepass.ports.out.Reloj;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unico E2E del taller: compra -> bloqueo -> pago VERIFICADO -> boleto firmado ->
 * validacion en puerta -> vencimiento -> liberacion. Es la verificacion ADR-0001 y
 * corre contra contenedor real + SQLite temporal + reloj controlado por el test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlujoCompletoE2ETest {

    private static final Instant BASE = Instant.parse("2026-10-16T08:00:00Z");

    // SQLite efimero para este test: Boot corre schema.sql y data.sql contra este archivo.
    private static final Path SQLITE_E2E = creaArchivoTemporal();

    private static final MongoDBContainer MONGO_E2E = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    static Path creaArchivoTemporal() {
        try {
            return Files.createTempFile("stagepass-e2e", ".db");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo crear la base E2E", e);
        }
    }

    @DynamicPropertySource
    static void infraestructuraDePrueba(DynamicPropertyRegistry registro) {
        // El contenedor arranca ANTES de construir el contexto de Spring.
        MONGO_E2E.start();
        registro.add("spring.mongodb.uri", () -> MONGO_E2E.getReplicaSetUrl("stagepass-e2e"));
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + SQLITE_E2E);
    }

    /**
     * Reloj de laboratorio: el test mueve la hora sin esperar los 10 minutos reales.
     * Es el mismo puerto Reloj que en produccion inyecta SistemaReloj.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class RelojDePrueba {
        static final AtomicReference<Instant> AHORA = new AtomicReference<>(BASE);

        @Bean
        @Primary
        Reloj relojDelTest() {
            return () -> AHORA.get();
        }
    }

    @Value("${local.server.port}")
    private int puerto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MantenimientoUseCase mantenimiento;

    private record RespuestaInicio(String ordenId, String codigoPago, String expiraEn) {}
    private record RespuestaBoleto(String boletoId, String ordenId, String codigoRedencion, String firmaHash) {}
    private record Veredicto(boolean valido, String motivo) {}

    @Test
    void flujoCompletoDeCompraPagoValidacionYExpiracion() {
        RestClient cliente = RestClient.builder().baseUrl("http://localhost:" + puerto).build();

        // 1. La compra arranca la reserva y devuelve la ventana de 10 minutos.
        RespuestaInicio inicio = postear(cliente, "/api/v1/compras", Map.of(
                "eventoId", "EV-E2E", "clienteId", "CL-001",
                "asientosId", List.of("VIP-A1", "VIP-A2")), RespuestaInicio.class);
        assertThat(inicio.codigoPago()).startsWith("PAY-");
        assertThat(inicio.expiraEn()).isNotBlank();

        // 2. Las dos sillas quedaron BLOQUEADAS en el catalogo legacy.
        assertThat(estadoDe("VIP-A1")).isEqualTo("BLOQUEADO");
        assertThat(estadoDe("VIP-A2")).isEqualTo("BLOQUEADO");

        // 3. El pago se verifica contra la pasarela mock y se emite la boleta firmada.
        RespuestaBoleto boleto = postear(cliente, "/api/v1/pagos/confirmar", Map.of(
                "ordenId", inicio.ordenId(), "pasarela", "wompi",
                "identificadorTransaccion", "OK-777"), RespuestaBoleto.class);
        assertThat(boleto.codigoRedencion()).startsWith("SP-");

        // 4. Al aprobarse el cobro, las sillas pasan a VENDIDAS.
        assertThat(estadoDe("VIP-A1")).isEqualTo("VENDIDO");

        // 5. El QR apunta al endpoint de puerta; la firma se revalido tras releer en Mongo.
        Veredicto veredicto = cliente.get().uri("/api/v1/boletos/{codigo}", boleto.codigoRedencion())
                .retrieve()
                .body(Veredicto.class);
        assertThat(veredicto).isNotNull();
        assertThat(veredicto.valido()).isTrue();

        // 6. Segunda compra del mismo cliente para probar la expiracion automatica.
        RespuestaInicio segunda = postear(cliente, "/api/v1/compras", Map.of(
                "eventoId", "EV-E2E", "clienteId", "CL-001",
                "asientosId", List.of("VIP-A3", "GEN-01")), RespuestaInicio.class);
        assertThat(estadoDe("VIP-A3")).isEqualTo("BLOQUEADO");

        // El reloj del test avanza 11 minutos de una vez, por encima de la ventana.
        RelojDePrueba.AHORA.set(BASE.plusSeconds(660));
        int expiradas = mantenimiento.expirarReservasVencidas();
        assertThat(expiradas).isEqualTo(1);
        assertThat(estadoDe("VIP-A3")).isEqualTo("DISPONIBLE");
        assertThat(estadoDe("GEN-01")).isEqualTo("DISPONIBLE");

        // 7. El barrido ya dejo la orden EXPIRADA: pagarla de nuevo toca el guard de doble
        // pago del nucleo, que responde 409 porque el estado ya no es pendiente.
        int codigoTrasElBarrido = postearEstadoDeError(cliente, "/api/v1/pagos/confirmar", Map.of(
                "ordenId", segunda.ordenId(), "pasarela", "wompi",
                "identificadorTransaccion", "OK-778"));
        assertThat(codigoTrasElBarrido).isEqualTo(409);

        // 8. Ventana vencida pero el barrido SIN correr: ConfirmarPago la marca expirada
        // de inmediato y responde 410 Gone con la OrdenExpiradaException del nucleo.
        RespuestaInicio tercera = postear(cliente, "/api/v1/compras", Map.of(
                "eventoId", "EV-E2E", "clienteId", "CL-001",
                "asientosId", List.of("VIP-A4", "GEN-02")), RespuestaInicio.class);
        RelojDePrueba.AHORA.set(BASE.plusSeconds(1320));
        int codigoVencidaSinBarrido = postearEstadoDeError(cliente, "/api/v1/pagos/confirmar", Map.of(
                "ordenId", tercera.ordenId(), "pasarela", "wompi",
                "identificadorTransaccion", "OK-779"));
        assertThat(codigoVencidaSinBarrido).isEqualTo(410);
    }

    private String estadoDe(String asientoId) {
        return jdbc.queryForObject("SELECT estado FROM asientos WHERE id = ?", String.class, asientoId);
    }

    private <T> T postear(RestClient cliente, String ruta, Object cuerpo, Class<T> tipoRespuesta) {
        return cliente.post().uri(ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(tipoRespuesta);
    }

    /** Para las respuestas de error no se usa retrieve() (lanza) sino exchange directo. */
    private int postearEstadoDeError(RestClient cliente, String ruta, Object cuerpo) {
        return cliente.post().uri(ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .exchange((peticion, respuesta) -> respuesta.getStatusCode().value());
    }
}
