package com.stagepass.domain.service;

import com.stagepass.domain.model.Cliente;
import com.stagepass.domain.model.OrdenCompra;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PoliticaExpiracionTest {

    private static final Instant INICIO = Instant.parse("2026-10-15T12:00:00Z");

    private final PoliticaExpiracion politica = new PoliticaExpiracion();

    private OrdenCompra ordenPendiente() {
        var cliente = new Cliente("c-1", "Ana Perez", "ana@correo.com", "3001234567", "CO");
        return new OrdenCompra("orden-1", "evento-1", cliente, List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("110000.00"),
                politica.calcularVencimiento(INICIO));
    }

    @Test
    void laVentanaDeBloqueoEsDeDiezMinutos() {
        assertThat(PoliticaExpiracion.VENTANA_DE_BLOQUEO).isEqualTo(Duration.ofMinutes(10));
        assertThat(politica.calcularVencimiento(INICIO))
                .isEqualTo(INICIO.plus(Duration.ofMinutes(10)));
    }

    @Test
    void noExpiraAntesDelVencimiento() {
        assertThat(politica.expiro(ordenPendiente(), INICIO.plus(Duration.ofMinutes(9)))).isFalse();
    }

    @Test
    void expiraPasadaLaVentana() {
        // 10 minutos y un segundo despues: la ventana ya se cerro.
        Instant despues = INICIO.plus(Duration.ofMinutes(10)).plusSeconds(1);

        assertThat(politica.expiro(ordenPendiente(), despues)).isTrue();
    }

    @Test
    void unaOrdenAprobadaNoExpira() {
        var orden = ordenPendiente();
        orden.aprobar();

        assertThat(politica.expiro(orden, INICIO.plus(Duration.ofMinutes(11)))).isFalse();
    }
}
