package com.stagepass.infrastructure.in.web;

import com.stagepass.domain.exception.OrdenExpiradaException;
import com.stagepass.domain.model.Asiento;
import com.stagepass.domain.model.Cliente;
import com.stagepass.domain.model.EstadoOrden;
import com.stagepass.domain.model.OrdenCompra;
import com.stagepass.ports.in.CheckoutUseCase;
import com.stagepass.ports.in.IniciarCompraResponse;
import com.stagepass.ports.in.SolicitudCompra;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integracion del adaptador HTTP de compras contra el puerto primario (mockeado). */
class CheckoutControllerTest {

    private CheckoutUseCase checkout;
    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        checkout = mock(CheckoutUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CheckoutController(checkout))
                .setControllerAdvice(new ManejadorDeErrores())
                .build();
    }

    @Test
    void iniciarCompraResponde201ConOrdenCodigoYVencimiento() throws Exception {
        when(checkout.iniciarCompra(any())).thenReturn(
                new IniciarCompraResponse("ORD-123", "PAY-ORD1234", "2026-10-16T08:10:00Z"));

        mockMvc.perform(post("/api/v1/compras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventoId":"EV-001","clienteId":"CL-001",
                                 "asientosId":["VIP-A1","GEN-01"]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ordenId").value("ORD-123"))
                .andExpect(jsonPath("$.codigoPago").value("PAY-ORD1234"));
    }

    @Test
    void iniciarCompraPideLosTresDatosDelFormulario() throws Exception {
        // Falta clienteId: la validacion de entrada del borde rechaza antes del nucleo.
        mockMvc.perform(post("/api/v1/compras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventoId":"EV-001","asientosId":["VIP-A1"]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("clienteId")));
    }

    @Test
    void iniciarCompraTraduceElCuerpoAlPuertoSinTransformarNada() throws Exception {
        when(checkout.iniciarCompra(any())).thenReturn(
                new IniciarCompraResponse("ORD-1", "PAY-ORD1", "2026-10-16T08:10:00Z"));

        mockMvc.perform(post("/api/v1/compras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventoId":"EV-9","clienteId":"CL-9","asientosId":["GEN-01"]}"""))
                .andExpect(status().isCreated());

        ArgumentCaptor<SolicitudCompra> captura = ArgumentCaptor.forClass(SolicitudCompra.class);
        verify(checkout).iniciarCompra(captura.capture());
        assertThat(captura.getValue().eventoId()).isEqualTo("EV-9");
        assertThat(captura.getValue().clienteId()).isEqualTo("CL-9");
        assertThat(captura.getValue().asientosId()).containsExactly("GEN-01");
    }

    @Test
    void seleccionarDevuelveElResumenCompletoDeLaOrden() throws Exception {
        when(checkout.seleccionarAsientos(eq("ORD-55"), any())).thenReturn(ordenDePrueba());

        mockMvc.perform(post("/api/v1/compras/ORD-55/seleccion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"asientosId":["VIP-A1"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordenId").value("ORD-55"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.montoTotal").value("165000.00"))
                .andExpect(jsonPath("$.asientos[0].codigoUbicacion").value("VIP-A1"));
    }

    @Test
    void seleccionDeUnaOrdenVencidaResponde410() throws Exception {
        when(checkout.seleccionarAsientos(any(), any()))
                .thenThrow(new OrdenExpiradaException("La orden ORD-55 vencio"));

        mockMvc.perform(post("/api/v1/compras/ORD-55/seleccion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"asientosId":["VIP-A1"]}"""))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.detail").value("La orden ORD-55 vencio"));
    }

    @Test
    void unCuerpoJsonMalformadoResponde400YNoCaeEn500() throws Exception {
        mockMvc.perform(post("/api/v1/compras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{eventoId roto"))
                .andExpect(status().isBadRequest());
    }

    /** Orden de laboratorio para el resumen: una VIP de 150000 + tarifa 10% = 165000. */
    private OrdenCompra ordenDePrueba() {
        Asiento vip = new Asiento("VIP-A1", "REC-01", "VIP-A1", new BigDecimal("150000.00"));
        return new OrdenCompra("ORD-55", "EV-001",
                new Cliente("CL-001", "Camila Osorio", "camila.osorio@example.com", "573001234567", "CO"),
                List.of(vip), new BigDecimal("15000.00"), new BigDecimal("0.00"), new BigDecimal("165000.00"),
                Instant.parse("2026-10-16T08:10:00.123456789Z"));
    }
}
