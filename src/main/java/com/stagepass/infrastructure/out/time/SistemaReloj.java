package com.stagepass.infrastructure.out.time;

import com.stagepass.ports.out.Reloj;

import java.time.Instant;

/**
 * Adaptador del tiempo: UNICO lugar del sistema donde vive Instant.now(). Todo el
 * nucleo consulta este puerto, por eso los tests fijan la hora y no esperan minutos.
 */
public class SistemaReloj implements Reloj {

    @Override
    public Instant ahora() {
        return Instant.now();
    }
}
