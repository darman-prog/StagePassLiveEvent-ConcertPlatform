package com.stagepass.application.dobles;

import com.stagepass.ports.out.Notificacion;
import com.stagepass.ports.out.Notificador;

import java.util.ArrayList;
import java.util.List;

/** Notificador de mentira: en vez de enviar, guarda los mensajes para poder revisarlos. */
public class NotificadorFalso implements Notificador {

    private final List<Notificacion> enviadas = new ArrayList<>();

    @Override
    public void enviar(Notificacion notificacion) {
        enviadas.add(notificacion);
    }

    public List<Notificacion> enviadas() {
        return enviadas;
    }
}
