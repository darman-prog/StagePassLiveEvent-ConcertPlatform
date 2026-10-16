package com.stagepass.ports.in;

/** Puerto primario de la validacion en puerta: recalcula la firma y responde valido o no. */
public interface ValidacionUseCase {

    ResultadoValidacion validarBoleto(String codigoRedencion);
}
