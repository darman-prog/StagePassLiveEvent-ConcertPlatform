package com.stagepass.application;

import com.stagepass.application.dobles.AsientoRepositoryEnMemoria;
import com.stagepass.application.dobles.OrdenRepositoryEnMemoria;
import com.stagepass.application.dobles.RelojFijo;
import com.stagepass.domain.model.Asiento;
import com.stagepass.domain.model.Cliente;
import com.stagepass.domain.model.EstadoAsiento;
import com.stagepass.domain.model.EstadoOrden;
import com.stagepass.domain.model.OrdenCompra;
import com.stagepass.domain.service.PoliticaExpiracion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpirarReservasTest {

    private static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");

    private AsientoRepositoryEnMemoria asientos;
    private OrdenRepositoryEnMemoria ordenes;
    private ExpirarReservas casoDeUso;

    @BeforeEach
    void preparar() {
        asientos = new AsientoRepositoryEnMemoria();
        ordenes = new OrdenRepositoryEnMemoria();
        casoDeUso = new ExpirarReservas(ordenes, asientos, new PoliticaExpiracion(), new RelojFijo(AHORA));
    }

    @Test
    void liberaLasSillasDeLasOrdenesVencidasYRespetaLasVigentes() {
        // Orden vencida: su ventana ya paso hace un minuto.
        Asiento viejo1 = agregarAsiento("v-1");
        Asiento viejo2 = agregarAsiento("v-2");
        ordenes.guardar(orden("orden-vieja", List.of(viejo1, viejo2), AHORA.minusSeconds(60)));
        asientos.bloquear(List.of("v-1", "v-2"), AHORA.minusSeconds(60));

        // Orden vigente: todavia tiene sus 10 minutos corriendo.
        Asiento vivo = agregarAsiento("n-1");
        ordenes.guardar(orden("orden-nueva", List.of(vivo), AHORA.plus(Duration.ofMinutes(10))));
        asientos.bloquear(List.of("n-1"), AHORA.plus(Duration.ofMinutes(10)));

        int expiradas = casoDeUso.expirarReservasVencidas();

        assertThat(expiradas).isEqualTo(1);
        assertThat(ordenes.buscarPorId("orden-vieja").getEstado()).isEqualTo(EstadoOrden.EXPIRADA);
        assertThat(asientos.buscarPorId("v-1").getEstado()).isEqualTo(EstadoAsiento.DISPONIBLE);
        assertThat(asientos.buscarPorId("v-2").getEstado()).isEqualTo(EstadoAsiento.DISPONIBLE);

        // La orden vigente no se toca.
        assertThat(ordenes.buscarPorId("orden-nueva").getEstado()).isEqualTo(EstadoOrden.PENDIENTE_PAGO);
        assertThat(asientos.buscarPorId("n-1").getEstado()).isEqualTo(EstadoAsiento.BLOQUEADO);
    }

    @Test
    void noHaceNadaCuandoNoHayOrdenesVencidas() {
        Asiento vivo = agregarAsiento("n-1");
        ordenes.guardar(orden("orden-nueva", List.of(vivo), AHORA.plus(Duration.ofMinutes(10))));

        assertThat(casoDeUso.expirarReservasVencidas()).isZero();
    }

    private Asiento agregarAsiento(String id) {
        Asiento asiento = new Asiento(id, "recinto-1", "G-" + id, new BigDecimal("90000"));
        asientos.agregar(asiento);
        return asiento;
    }

    private OrdenCompra orden(String id, List<Asiento> sillas, Instant expiraEn) {
        return new OrdenCompra(id, "evento-1",
                new Cliente("cliente-1", "Ana", "ana@correo.test", "3001234567", "CO"),
                sillas, new BigDecimal("10000.00"), BigDecimal.ZERO, new BigDecimal("190000.00"), expiraEn);
    }
}
