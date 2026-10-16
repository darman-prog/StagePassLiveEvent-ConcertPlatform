package com.stagepass.domain.service;

import com.stagepass.domain.exception.LimiteBoletasExcedidoException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidadorLimitesTest {

    private final ValidadorLimites validador = new ValidadorLimites();

    @Test
    void permiteLlegarExactamenteASeisBoletas() {
        assertThatCode(() -> validador.validarCompra("cliente-1", "evento-1", 5, 1))
                .doesNotThrowAnyException();
    }

    @Test
    void rechazaLaSeptimaBoletaAcumulada() {
        assertThatThrownBy(() -> validador.validarCompra("cliente-1", "evento-1", 5, 2))
                .isInstanceOf(LimiteBoletasExcedidoException.class)
                .hasMessageContaining("El maximo es 6");
    }
}
