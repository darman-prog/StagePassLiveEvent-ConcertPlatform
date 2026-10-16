package com.stagepass.infrastructure.in.web;

import com.stagepass.ports.in.ResultadoValidacion;
import com.stagepass.ports.in.ValidacionUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integracion del adaptador HTTP de validacion en puerta. */
class ValidacionControllerTest {

    private ValidacionUseCase validacion;
    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        validacion = Mockito.mock(ValidacionUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ValidacionController(validacion))
                .setControllerAdvice(new ManejadorDeErrores())
                .build();
    }

    @Test
    void unBoletoValidoResponde200ConSuMotivo() throws Exception {
        when(validacion.validarBoleto("SP-1A2B3C4D"))
                .thenReturn(new ResultadoValidacion(true, "Boleto valido"));

        mockMvc.perform(get("/api/v1/boletos/SP-1A2B3C4D"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true))
                .andExpect(jsonPath("$.motivo").value("Boleto valido"));
    }

    @Test
    void unBoletoAlteradoResponde200PeroInvalido() throws Exception {
        // La puerta recibe 200 (la consulta sirvio) con el veredicto del negocio.
        when(validacion.validarBoleto("SP-XXXX0000"))
                .thenReturn(new ResultadoValidacion(false, "La firma no coincide: el boleto fue alterado"));

        mockMvc.perform(get("/api/v1/boletos/SP-XXXX0000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(false));
    }
}
