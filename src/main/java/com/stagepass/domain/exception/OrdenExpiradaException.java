package com.stagepass.domain.exception;

/** Se lanza cuando se intenta pagar una orden cuya ventana de 10 minutos ya vencio. */
public class OrdenExpiradaException extends ExcepcionNegocio {

    public OrdenExpiradaException(String mensaje) {
        super(mensaje);
    }
}
