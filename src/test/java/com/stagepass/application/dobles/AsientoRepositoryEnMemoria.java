package com.stagepass.application.dobles;

import com.stagepass.domain.model.Asiento;
import com.stagepass.ports.out.AsientoRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Catalogo de asientos de mentira: guarda en un mapa y aplica los cambios de estado en memoria. */
public class AsientoRepositoryEnMemoria implements AsientoRepository {

    private final Map<String, Asiento> porId = new LinkedHashMap<>();

    public void agregar(Asiento asiento) {
        porId.put(asiento.getId(), asiento);
    }

    @Override
    public List<Asiento> listarPorRecinto(String recintoId) {
        return porId.values().stream()
                .filter(asiento -> asiento.getRecintoId().equals(recintoId))
                .toList();
    }

    @Override
    public Asiento buscarPorId(String asientoId) {
        return porId.get(asientoId);
    }

    @Override
    public void bloquear(List<String> asientosId, Instant hasta) {
        asientosId.forEach(id -> porId.get(id).bloquear(hasta));
    }

    @Override
    public void marcarVendidos(List<String> asientosId) {
        asientosId.forEach(id -> porId.get(id).vender());
    }

    @Override
    public void liberar(List<String> asientosId) {
        asientosId.forEach(id -> porId.get(id).liberar());
    }
}
