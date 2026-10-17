package com.stagepass.infrastructure.in.web;

import com.stagepass.ports.in.CatalogoUseCase;
import com.stagepass.ports.in.CatalogoUseCase.AsientoDeRecinto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogoControllerTest {

    private CatalogoUseCase catalogo;
    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        catalogo = Mockito.mock(CatalogoUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CatalogoController(catalogo))
                .setControllerAdvice(new ManejadorDeErrores())
                .build();
    }

    @Test
    void devuelve200ConLaListaDeAsientosDelRecinto() throws Exception {
        when(catalogo.listarAsientos("REC-01")).thenReturn(List.of(
                new AsientoDeRecinto("VIP-A1", "VIP-A1", "150000.00", "DISPONIBLE"),
                new AsientoDeRecinto("VIP-A2", "VIP-A2", "150000.00", "BLOQUEADO")
        ));

        mockMvc.perform(get("/api/v1/recintos/REC-01/asientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("VIP-A1"))
                .andExpect(jsonPath("$[0].codigoUbicacion").value("VIP-A1"))
                .andExpect(jsonPath("$[0].precio").value("150000.00"))
                .andExpect(jsonPath("$[0].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$[1].id").value("VIP-A2"))
                .andExpect(jsonPath("$[1].estado").value("BLOQUEADO"));
    }

    @Test
    void devuelve200ConListaVaciaCuandoElRecintoNoTieneAsientos() throws Exception {
        when(catalogo.listarAsientos("REC-99")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/recintos/REC-99/asientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
