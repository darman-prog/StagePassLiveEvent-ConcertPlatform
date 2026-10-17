package com.stagepass.application;

import com.stagepass.application.dobles.AsientoRepositoryEnMemoria;
import com.stagepass.domain.model.Asiento;
import com.stagepass.ports.in.CatalogoUseCase.AsientoDeRecinto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListarAsientosTest {

    private AsientoRepositoryEnMemoria repositorio;
    private ListarAsientos casoDeUso;

    @BeforeEach
    void preparar() {
        repositorio = new AsientoRepositoryEnMemoria();
        casoDeUso = new ListarAsientos(repositorio);
    }

    @Test
    void listaLosAsientosDelRecintoConPrecioYEstadoEnTexto() {
        Asiento libre = new Asiento("VIP-A1", "REC-01", "VIP-A1", new BigDecimal("150000.00"));
        Asiento bloqueado = new Asiento("VIP-A2", "REC-01", "VIP-A2", new BigDecimal("150000.00"));
        bloqueado.bloquear(Instant.now().plusSeconds(600));
        Asiento vendido = new Asiento("GEN-01", "REC-01", "General-01", new BigDecimal("80000.00"));
        vendido.vender();
        Asiento otroRecinto = new Asiento("BOX-01", "REC-02", "Palco-01", new BigDecimal("220000.00"));

        repositorio.agregar(libre);
        repositorio.agregar(bloqueado);
        repositorio.agregar(vendido);
        repositorio.agregar(otroRecinto);

        List<AsientoDeRecinto> asientos = casoDeUso.listarAsientos("REC-01");

        assertThat(asientos).hasSize(3);
        assertThat(asientos).extracting(AsientoDeRecinto::id)
                .containsExactly("VIP-A1", "VIP-A2", "GEN-01");
        assertThat(asientos).extracting(AsientoDeRecinto::precio)
                .containsExactly("150000.00", "150000.00", "80000.00");
        assertThat(asientos).extracting(AsientoDeRecinto::estado)
                .containsExactly("DISPONIBLE", "BLOQUEADO", "VENDIDO");
    }

    @Test
    void unRecintoSinAsientosDevuelveListaVacia() {
        List<AsientoDeRecinto> asientos = casoDeUso.listarAsientos("REC-99");

        assertThat(asientos).isEmpty();
    }
}
