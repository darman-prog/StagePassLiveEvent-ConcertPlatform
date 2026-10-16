package com.stagepass.infrastructure.in.web;

import com.stagepass.domain.exception.ExcepcionNegocio;
import com.stagepass.domain.exception.PagoNoVerificadoException;
import com.stagepass.domain.model.Boleto;
import com.stagepass.ports.in.ConfirmacionPago;
import com.stagepass.ports.in.PagosUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integracion del adaptador HTTP de pagos: confirmacion activa y webhook. */
class PagosControllerTest {

    private PagosUseCase pagos;
    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        pagos = mock(PagosUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PagosController(pagos))
                .setControllerAdvice(new ManejadorDeErrores())
                .build();
    }

    @Test
    void confirmarPagoResponde200ConLaBoletaEmitida() throws Exception {
        when(pagos.confirmarPago(any())).thenReturn(
                new Boleto("BLT-9", "ORD-9", "SP-1A2B3C4D", "firmahash", Instant.parse("2026-10-16T08:05:00Z")));

        mockMvc.perform(post("/api/v1/pagos/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ordenId":"ORD-9","pasarela":"wompi",
                                 "identificadorTransaccion":"OK-1250"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boletoId").value("BLT-9"))
                .andExpect(jsonPath("$.codigoRedencion").value("SP-1A2B3C4D"));
    }

    @Test
    void laPasarelaViajaNormalizadaEnMinusculasAlNucleo() throws Exception {
        when(pagos.confirmarPago(any()))
                .thenReturn(new Boleto("BLT-1", "ORD-1", "SP-AAAA1111", "firma", Instant.now()));

        mockMvc.perform(post("/api/v1/pagos/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ordenId":"ORD-1","pasarela":"WOMPI",
                                 "identificadorTransaccion":"OK-1250"}"""))
                .andExpect(status().isOk());

        ArgumentCaptor<ConfirmacionPago> captura = ArgumentCaptor.forClass(ConfirmacionPago.class);
        verify(pagos).confirmarPago(captura.capture());
        assertThat(captura.getValue().pasarela()).isEqualTo("wompi");
    }

    @Test
    void unPagoNoVerificadoResponde402PaymentRequired() throws Exception {
        when(pagos.confirmarPago(any()))
                .thenThrow(new PagoNoVerificadoException("La transaccion TX-1 no llego aprobada"));

        mockMvc.perform(post("/api/v1/pagos/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ordenId":"ORD-2","pasarela":"pse",
                                 "identificadorTransaccion":"TX-1"}"""))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.detail").value("La transaccion TX-1 no llego aprobada"));
    }

    @Test
    void confirmarUnaOrdenInexistenteResponde404() throws Exception {
        when(pagos.confirmarPago(any()))
                .thenThrow(new ExcepcionNegocio("La orden ORD-404 no existe"));

        mockMvc.perform(post("/api/v1/pagos/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ordenId":"ORD-404","pasarela":"wompi",
                                 "identificadorTransaccion":"OK-1"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmarConUnaPasarelaNoRegistradaResponde409() throws Exception {
        when(pagos.confirmarPago(any()))
                .thenThrow(new ExcepcionNegocio("La pasarela striper no esta registrada"));

        mockMvc.perform(post("/api/v1/pagos/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ordenId":"ORD-3","pasarela":"striper",
                                 "identificadorTransaccion":"OK-1"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void elWebhookAcusaReciboYReenviaElAvisoAlPuerto() throws Exception {
        mockMvc.perform(post("/api/v1/pagos/webhook/wompi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identificadorTransaccion":"TX-77","aprobado":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recibido").value(true));

        ArgumentCaptor<com.stagepass.ports.in.NotificacionTransaccion> captura =
                ArgumentCaptor.forClass(com.stagepass.ports.in.NotificacionTransaccion.class);
        verify(pagos).notificarCambioTransaccion(eq("wompi"), captura.capture());
        assertThat(captura.getValue().identificadorTransaccion()).isEqualTo("TX-77");
        assertThat(captura.getValue().aprobado()).isTrue();
    }

    private static <T> T eq(T valor) {
        return org.mockito.ArgumentMatchers.eq(valor);
    }
}
