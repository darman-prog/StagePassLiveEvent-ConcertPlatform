package com.stagepass.domain.exception;

/** Se lanza cuando alguien pide un asiento que no esta libre. */
public class AsientoNoDisponibleException extends ExcepcionNegocio {

    public AsientoNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
