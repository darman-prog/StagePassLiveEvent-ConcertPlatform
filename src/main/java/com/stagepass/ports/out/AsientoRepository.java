package com.stagepass.ports.out;

import com.stagepass.domain.model.Asiento;

import java.time.Instant;
import java.util.List;

/**
 * Lo que el nucleo necesita del catalogo legacy de asientos, sin saber como esta
 * guardado. Siempre devuelve entidades del dominio: nunca un ResultSet ni un DTO.
 */
public interface AsientoRepository {

    List<Asiento> listarPorRecinto(String recintoId);

    Asiento buscarPorId(String asientoId);

    void bloquear(List<String> asientosId, Instant hasta);

    void marcarVendidos(List<String> asientosId);

    void liberar(List<String> asientosId);
}
