package com.stagepass.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AsientoTest {

    private static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");

    private Asiento asiento() {
        return new Asiento("a-1", "recinto-1", "VIP-A12", new BigDecimal("100000"));
    }

    @Test
    void naceDisponible() {
        assertThat(asiento().estaDisponible()).isTrue();
    }

    @Test
    void bloqueadoVenceCuandoPasaLaHoraLimite() {
        var asiento = asiento();
        asiento.bloquear(AHORA.plusSeconds(60));

        assertThat(asiento.estaDisponible()).isFalse();
        assertThat(asiento.bloqueoVencido(AHORA.plusSeconds(59))).isFalse();
        assertThat(asiento.bloqueoVencido(AHORA.plusSeconds(61))).isTrue();
    }

    @Test
    void vendidoDejaDeTenerBloqueo() {
        var asiento = asiento();
        asiento.bloquear(AHORA.plusSeconds(60));
        asiento.vender();

        assertThat(asiento.getEstado()).isEqualTo(EstadoAsiento.VENDIDO);
        assertThat(asiento.bloqueoVencido(AHORA.plusSeconds(120))).isFalse();
    }

    @Test
    void liberarDevuelveElAsientoAlPool() {
        var asiento = asiento();
        asiento.bloquear(AHORA.plusSeconds(60));
        asiento.liberar();

        assertThat(asiento.estaDisponible()).isTrue();
        assertThat(asiento.bloqueoVencido(AHORA.plusSeconds(120))).isFalse();
    }
}
