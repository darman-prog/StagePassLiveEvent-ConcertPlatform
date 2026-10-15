package com.stagepass.domain.exception;

/** Se lanza cuando el pago no llego aprobado por la pasarela. */
public class PagoNoVerificadoException extends ExcepcionNegocio {

    public PagoNoVerificadoException(String mensaje) {
        super(mensaje);
    }
}
