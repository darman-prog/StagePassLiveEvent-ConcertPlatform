package com.stagepass.domain.exception;

/**
 * Base de todos los errores de negocio. Los adaptadores primarios la mapean a un codigo
 * HTTP con criterio; si el dominio tirara RuntimeException sueltas no habria como distinguirlos.
 */
public class ExcepcionNegocio extends RuntimeException {

    public ExcepcionNegocio(String mensaje) {
        super(mensaje);
    }
}
