package com.stagepass.ports.in;

/** Cambio de estado de una transaccion, tal como lo reporta la pasarela por webhook. */
public record NotificacionTransaccion(String identificadorTransaccion, boolean aprobado) {}
