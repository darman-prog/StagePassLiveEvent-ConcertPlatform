package com.stagepass.ports.out;

/** Mensaje saliente para el comprador, por correo o WhatsApp. */
public record Notificacion(String destinatario, String mensaje) {}
