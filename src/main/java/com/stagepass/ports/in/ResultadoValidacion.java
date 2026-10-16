package com.stagepass.ports.in;

/** Respuesta de la validacion en puerta: si el boleto vale y por que. */
public record ResultadoValidacion(boolean valido, String motivo) {}
