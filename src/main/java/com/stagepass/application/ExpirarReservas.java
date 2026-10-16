package com.stagepass.application;

import com.stagepass.domain.model.Asiento;
import com.stagepass.domain.model.OrdenCompra;
import com.stagepass.domain.service.PoliticaExpiracion;
import com.stagepass.ports.in.MantenimientoUseCase;
import com.stagepass.ports.out.AsientoRepository;
import com.stagepass.ports.out.OrdenRepository;
import com.stagepass.ports.out.Reloj;

import java.time.Instant;
import java.util.List;

/**
 * Barrido del job de expiracion: marca las ordenes vencidas y devuelve sus sillas al
 * pool para que otro comprador las pueda tomar.
 */
public class ExpirarReservas implements MantenimientoUseCase {

    private final OrdenRepository ordenes;
    private final AsientoRepository asientos;
    private final PoliticaExpiracion politicaExpiracion;
    private final Reloj reloj;

    public ExpirarReservas(OrdenRepository ordenes, AsientoRepository asientos,
                           PoliticaExpiracion politicaExpiracion, Reloj reloj) {
        this.ordenes = ordenes;
        this.asientos = asientos;
        this.politicaExpiracion = politicaExpiracion;
        this.reloj = reloj;
    }

    @Override
    public int expirarReservasVencidas() {

        Instant ahora = reloj.ahora();
        List<OrdenCompra> vencidas = ordenes.buscarVencidas(ahora);

        int expiradas = 0;
        for (OrdenCompra orden : vencidas) {
            // Doble chequeo: si el adaptador devolvio una orden que todavia vive, no se toca.
            if (!politicaExpiracion.expiro(orden, ahora)) {
                continue;
            }
            orden.expirar();
            ordenes.guardar(orden);

            List<String> idsAsientos = orden.getAsientos().stream().map(Asiento::getId).toList();
            asientos.liberar(idsAsientos);
            expiradas++;
        }
        return expiradas;
    }
}
