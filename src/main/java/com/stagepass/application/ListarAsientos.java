package com.stagepass.application;

import com.stagepass.domain.model.Asiento;
import com.stagepass.ports.in.CatalogoUseCase;
import com.stagepass.ports.out.AsientoRepository;

import java.util.List;

/**
 * Caso de uso de lectura del mapa de asientos de un recinto.
 * Delega en el repositorio secundario (que libera bloqueos vencidos al leer).
 */
public class ListarAsientos implements CatalogoUseCase {

    private final AsientoRepository asientos;

    public ListarAsientos(AsientoRepository asientos) {
        this.asientos = asientos;
    }

    @Override
    public List<AsientoDeRecinto> listarAsientos(String recintoId) {
        return asientos.listarPorRecinto(recintoId).stream()
                .map(asiento -> new AsientoDeRecinto(
                        asiento.getId(),
                        asiento.getCodigoUbicacion(),
                        asiento.getPrecioBase().toPlainString(),
                        asiento.getEstado().name()))
                .toList();
    }
}
