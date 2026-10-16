package com.stagepass.infrastructure.in.job;

import com.stagepass.ports.in.MantenimientoUseCase;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El job solo delega en el puerto primario: aqui se comprueba esa delegacion una a una. */
class ReservasJobTest {

    @Test
    void cadaCorridaDelBarridoDelegaEnElPuertoDeMantenimiento() {
        MantenimientoUseCase mantenimiento = mock(MantenimientoUseCase.class);
        when(mantenimiento.expirarReservasVencidas()).thenReturn(2);
        ReservasJob job = new ReservasJob(mantenimiento);

        job.expirarReservasVencidas();

        verify(mantenimiento, times(1)).expirarReservasVencidas();
    }
}
