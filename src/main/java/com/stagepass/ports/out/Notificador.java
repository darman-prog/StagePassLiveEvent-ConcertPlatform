package com.stagepass.ports.out;

/** Un solo puerto de notificaciones, dos implementaciones: Sendgrid y WhatsApp. */
public interface Notificador {

    void enviar(Notificacion notificacion);
}
