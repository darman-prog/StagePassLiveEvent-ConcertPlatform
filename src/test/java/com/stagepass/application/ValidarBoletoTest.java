package com.stagepass.application;

import com.stagepass.application.dobles.BoletoRepositoryEnMemoria;
import com.stagepass.application.dobles.OrdenRepositoryEnMemoria;
import com.stagepass.domain.model.Asiento;
import com.stagepass.domain.model.Boleto;
import com.stagepass.domain.model.Cliente;
import com.stagepass.domain.model.OrdenCompra;
import com.stagepass.domain.service.GeneradorFirma;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidarBoletoTest {

    private static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");
    private static final String CLAVE = "clave-test";

    private BoletoRepositoryEnMemoria boletos;
    private OrdenRepositoryEnMemoria ordenes;
    private GeneradorFirma generadorFirma;
    private ValidarBoleto casoDeUso;
    private OrdenCompra orden;

    @BeforeEach
    void preparar() {
        boletos = new BoletoRepositoryEnMemoria();
        ordenes = new OrdenRepositoryEnMemoria();
        generadorFirma = new GeneradorFirma();
        casoDeUso = new ValidarBoleto(boletos, ordenes, generadorFirma, CLAVE);

        Asiento a1 = new Asiento("a-1", "recinto-1", "VIP-A12", new BigDecimal("100000"));
        orden = new OrdenCompra("orden-1", "evento-1",
                new Cliente("cliente-1", "Ana", "ana@correo.test", "3001234567", "CO"),
                List.of(a1), new BigDecimal("10000.00"), BigDecimal.ZERO, new BigDecimal("110000.00"),
                AHORA.plusSeconds(600));
        orden.aprobar();
        ordenes.guardar(orden);
    }

    @Test
    void validaUnBoletoLegitimo() {
        boletos.guardar(boletoCon("SP-11111111", generadorFirma.generar(orden, CLAVE)));

        var resultado = casoDeUso.validarBoleto("SP-11111111");

        assertThat(resultado.valido()).isTrue();
    }

    @Test
    void detectaUnBoletoAlterado() {
        // Simula una base manipulada: la firma no corresponde a los datos actuales de la orden.
        boletos.guardar(boletoCon("SP-22222222", generadorFirma.generar(orden, "clave-falsa")));

        var resultado = casoDeUso.validarBoleto("SP-22222222");

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.motivo()).contains("no coincide");
    }

    @Test
    void rechazaUnCodigoQueNoExiste() {
        var resultado = casoDeUso.validarBoleto("SP-NADA");

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.motivo()).contains("no existe");
    }

    private Boleto boletoCon(String codigo, String firma) {
        return new Boleto("11111111-2222-3333-4444-555555555555", "orden-1", codigo, firma, AHORA);
    }
}
