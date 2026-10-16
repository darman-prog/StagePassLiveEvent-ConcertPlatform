package com.stagepass.infrastructure.in.job;

import com.stagepass.application.ExpirarReservas;
import com.stagepass.application.dobles.AsientoRepositoryEnMemoria;
import com.stagepass.application.dobles.OrdenRepositoryEnMemoria;
import com.stagepass.application.dobles.RelojFijo;
import com.stagepass.domain.model.Asiento;
import com.stagepass.domain.model.Cliente;
import com.stagepass.domain.model.EstadoOrden;
import com.stagepass.domain.model.OrdenCompra;
import com.stagepass.domain.service.PoliticaExpiracion;
import com.stagepass.ports.in.MantenimientoUseCase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    /** Del cron a la base en memoria: el reloj del test avanza 11 minutos de una vez. */
    @Test
    void unaCorridaRealExpiraLaOrdenYDevuelveSuSillaAlPool() {
        RelojFijo reloj = new RelojFijo(Instant.parse("2026-10-16T08:00:00Z"));
        AsientoRepositoryEnMemoria asientos = new AsientoRepositoryEnMemoria();
        Asiento gen01 = new Asiento("GEN-01", "REC-01", "General-01", new BigDecimal("80000.00"));
        asientos.agregar(gen01);

        OrdenRepositoryEnMemoria ordenes = new OrdenRepositoryEnMemoria();
        Cliente cliente = new Cliente("CL-001", "Camila Osorio", "camila.osorio@example.com", "573001234567", "CO");
        ordenes.guardar(new OrdenCompra("ORD-VENCIDA", "EV-001", cliente, List.of(gen01),
                new BigDecimal("8000.00"), new BigDecimal("0.00"), new BigDecimal("88000.00"),
                reloj.ahora().plus(Duration.ofMinutes(10))));

        // Simulacion del disparo del cron: avanza la hora y corre el caso de uso real.
        reloj.avanzar(Duration.ofMinutes(11));
        ReservasJob job = new ReservasJob(
                new ExpirarReservas(ordenes, asientos, new PoliticaExpiracion(), reloj));

        // El resultado observable del barrido: la orden quedo EXPIRADA y la silla libre.
        job.expirarReservasVencidas();

        assertThat(ordenes.buscarPorId("ORD-VENCIDA").getEstado()).isEqualTo(EstadoOrden.EXPIRADA);
        assertThat(gen01.estaDisponible()).isTrue();
    }
}
