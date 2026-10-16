package com.stagepass.ports.out;

import java.time.Instant;

/**
 * El Time Provider del material del curso: el nucleo nunca llama a Instant.now(),
 * consulta este puerto. Asi los tests fijan la hora y no esperan 10 minutos.
 */
public interface Reloj {

    Instant ahora();
}
