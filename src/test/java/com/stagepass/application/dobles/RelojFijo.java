package com.stagepass.application.dobles;

import com.stagepass.ports.out.Reloj;

import java.time.Duration;
import java.time.Instant;

/**
 * Reloj de mentira: devuelve siempre la hora fijada y permite avanzarla a mano, asi
 * la ventana de 10 minutos se prueba sin esperar en tiempo real.
 */
public class RelojFijo implements Reloj {

    private Instant actual;

    public RelojFijo(Instant inicial) {
        this.actual = inicial;
    }

    public void avanzar(Duration duracion) {
        this.actual = actual.plus(duracion);
    }

    @Override
    public Instant ahora() {
        return actual;
    }
}
