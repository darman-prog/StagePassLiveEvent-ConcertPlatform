package com.stagepass.domain.service;

import com.stagepass.domain.exception.LimiteBoletasExcedidoException;

/**
 * Regla 3: maximo 6 boletas acumuladas por cliente y evento. Recibe numeros, no entidades,
 * para que el test sea trivial y el caso de uso decida de donde salen los acumulados.
 */
public class ValidadorLimites {

    private static final int MAXIMO_POR_EVENTO = 6;

    public void validarCompra(String clienteId, String eventoId, int yaAdquiridas, int solicitadas) {

        int total = yaAdquiridas + solicitadas;

        if (total > MAXIMO_POR_EVENTO) {
            throw new LimiteBoletasExcedidoException(
                    "El cliente %s ya tiene %d boletas para el evento %s y pidio %d mas. El maximo es %d."
                            .formatted(clienteId, yaAdquiridas, eventoId, solicitadas, MAXIMO_POR_EVENTO));
        }
    }
}
