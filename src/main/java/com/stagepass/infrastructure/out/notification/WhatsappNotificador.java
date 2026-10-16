package com.stagepass.infrastructure.out.notification;

import com.stagepass.ports.out.Notificacion;
import com.stagepass.ports.out.Notificador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mock del canal de WhatsApp. Segunda implementacion del puerto Notificador: la
 * configuracion elige cual usar (el correo primario lleva el boleto del comprador).
 */
public class WhatsappNotificador implements Notificador {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsappNotificador.class);

    @Override
    public void enviar(Notificacion notificacion) {
        LOG.info("[WhatsApp] mensaje >>> {} | {}", notificacion.destinatario(), notificacion.mensaje());
    }
}
