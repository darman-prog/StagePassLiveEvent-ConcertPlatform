package com.stagepass.domain.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraTarifariaTest {

    private final CalculadoraTarifaria calculadora = new CalculadoraTarifaria();

    @Test
    void unaBoletaCobraDiezPorCientoDeTarifaYSinDescuento() {
        var resultado = calculadora.calcular(1, new BigDecimal("100000"));

        assertThat(resultado.tarifaServicio()).isEqualByComparingTo("10000.00");
        assertThat(resultado.descuento()).isEqualByComparingTo("0.00");
        assertThat(resultado.montoTotal()).isEqualByComparingTo("110000.00");
    }

    @Test
    void tresBoletasCobranOchoPorCientoYDescuentanCinco() {
        var resultado = calculadora.calcular(3, new BigDecimal("300000"));

        assertThat(resultado.tarifaServicio()).isEqualByComparingTo("24000.00");
        assertThat(resultado.descuento()).isEqualByComparingTo("15000.00");
        assertThat(resultado.montoTotal()).isEqualByComparingTo("309000.00");
    }

    @Test
    void cincoBoletasSiguenEnElTramoMedio() {
        var resultado = calculadora.calcular(5, new BigDecimal("500000"));

        assertThat(resultado.tarifaServicio()).isEqualByComparingTo("40000.00");
        assertThat(resultado.descuento()).isEqualByComparingTo("25000.00");
        assertThat(resultado.montoTotal()).isEqualByComparingTo("515000.00");
    }

    @Test
    void seisBoletasPasanAlTramoCoorporativo() {
        // El tramo cambia exactamente en el boleto 6: 5% de tarifa y 10% de descuento.
        var resultado = calculadora.calcular(6, new BigDecimal("600000"));

        assertThat(resultado.tarifaServicio()).isEqualByComparingTo("30000.00");
        assertThat(resultado.descuento()).isEqualByComparingTo("60000.00");
        assertThat(resultado.montoTotal()).isEqualByComparingTo("570000.00");
    }

    @Test
    void elTramoCambiaExactamenteEnElTercerBoleto() {
        // Con 2 boletas la tarifa es 10%; con 3 baja a 8%. Si el if se mueve, esto falla.
        var dos = calculadora.calcular(2, new BigDecimal("200000"));
        var tres = calculadora.calcular(3, new BigDecimal("300000"));

        assertThat(dos.tarifaServicio()).isEqualByComparingTo("20000.00");
        assertThat(tres.tarifaServicio()).isEqualByComparingTo("24000.00");
    }

    @Test
    void rechazaCantidadesYSubtotalesInvalidos() {
        assertThatThrownBy(() -> calculadora.calcular(0, new BigDecimal("100000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculadora.calcular(1, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
