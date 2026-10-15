package com.stagepass.domain.service;

import com.stagepass.domain.model.OrdenCompra;

import java.time.Duration;
import java.time.Instant;

/** Regla 2: ventana estricta de 10 minutos para pagar antes de liberar los asientos. */
public class PoliticaExpiracion {

    // Va como constante para que nadie la cambie "por un dia" sin que se note.
    public static final Duration VENTANA_DE_BLOQUEO = Duration.ofMinutes(10);

    public Instant calcularVencimiento(Instant inicio) {
        return inicio.plus(VENTANA_DE_BLOQUEO);
    }

    /** La orden ya sabe si su ventana vencio; la politica expone la regla del sistema. */
    public boolean expiro(OrdenCompra orden, Instant ahora) {
        return orden.expiro(ahora);
    }
}
