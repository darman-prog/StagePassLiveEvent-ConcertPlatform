package com.stagepass.infrastructure.in.job;

import com.stagepass.ports.in.MantenimientoUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Adaptador primario del barrido: cada minuto manda el caso de uso a cobrar las ordenes
 * cuya ventana de 10 minutos vencio. fixedDelay (no cron) evita que dos corridas se
 * solapen si la anterior sigue trabajando; el metodo del nucleo es idempotente.
 */
public class ReservasJob {

    private static final Logger LOG = LoggerFactory.getLogger(ReservasJob.class);

    private final MantenimientoUseCase mantenimiento;

    public ReservasJob(MantenimientoUseCase mantenimiento) {
        this.mantenimiento = mantenimiento;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    public void expirarReservasVencidas() {
        int expiradas = mantenimiento.expirarReservasVencidas();
        if (expiradas > 0) {
            LOG.info("[ReservasJob] {} ordenes vencieron: sillas devueltas al pool", expiradas);
        }
    }
}
