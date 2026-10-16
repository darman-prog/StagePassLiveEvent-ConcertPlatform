package com.stagepass.infrastructure.out.notification;

import com.stagepass.ports.out.Notificacion;
import com.stagepass.ports.out.Notificador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mock del canal de correo electronico via Sendgrid. Implementa el UNICO puerto
 * Notificador del nucleo; en produccion llamaria a la API de Sendgrid, aqui queda
 * huella en el log para poder demostrar el flujo en la defensa.
 */
public class SendgridNotificador implements Notificador {

    private static final Logger LOG = LoggerFactory.getLogger(SendgridNotificador.class);

    @Override
    public void enviar(Notificacion notificacion) {
        LOG.info("[Sendgrid] correo >>> {} | {}", notificacion.destinatario(), notificacion.mensaje());
    }
}
