package com.stagepass.domain.service;

import com.stagepass.domain.model.Asiento;
import com.stagepass.domain.model.Boleto;
import com.stagepass.domain.model.Cliente;
import com.stagepass.domain.model.OrdenCompra;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneradorFirmaTest {

    private final GeneradorFirma generador = new GeneradorFirma();

    private OrdenCompra ordenDePrueba(String montoTotal) {
        var asiento = new Asiento("a-1", "recinto-1", "VIP-A12", new BigDecimal("100000"));
        var cliente = new Cliente("c-1", "Ana Perez", "ana@correo.com", "3001234567", "CO");
        return new OrdenCompra("orden-1", "evento-1", cliente, List.of(asiento),
                new BigDecimal("10000.00"), BigDecimal.ZERO, new BigDecimal(montoTotal),
                Instant.parse("2026-10-15T12:10:00Z"));
    }

    @Test
    void laFirmaEsDeterministaYEnHexadecimal() {
        var orden = ordenDePrueba("110000.00");

        String primera = generador.generar(orden, "clave-evento");
        String segunda = generador.generar(orden, "clave-evento");

        assertThat(primera).isEqualTo(segunda).matches("[0-9a-f]{64}");
    }

    @Test
    void laFirmaCambiaSiSeAlteraElMonto() {
        String original = generador.generar(ordenDePrueba("110000.00"), "clave-evento");
        String alterada = generador.generar(ordenDePrueba("110001.00"), "clave-evento");

        assertThat(original).isNotEqualTo(alterada);
    }

    @Test
    void laFirmaCambiaSiCambiaLaClaveDelEvento() {
        var orden = ordenDePrueba("110000.00");

        assertThat(generador.generar(orden, "clave-evento"))
                .isNotEqualTo(generador.generar(orden, "otra-clave"));
    }

    @Test
    void elCodigoDeRedencionSeDerivaDelIdDelBoleto() {
        var boleto = new Boleto("12345678-0000-0000-0000-000000000000", "orden-1",
                "SP-12345678", "hash", Instant.parse("2026-10-15T12:00:00Z"));

        assertThat(generador.generarCodigoRedencion(boleto)).isEqualTo("SP-12345678");
    }
}
