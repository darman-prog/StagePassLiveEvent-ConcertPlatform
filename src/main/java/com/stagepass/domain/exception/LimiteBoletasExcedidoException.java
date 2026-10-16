package com.stagepass.domain.exception;

/** Se lanza cuando el cliente superaria las 6 boletas acumuladas para un evento. */
public class LimiteBoletasExcedidoException extends ExcepcionNegocio {

    public LimiteBoletasExcedidoException(String mensaje) {
        super(mensaje);
    }
}
