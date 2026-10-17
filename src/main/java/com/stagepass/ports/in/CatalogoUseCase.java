package com.stagepass.ports.in;

import java.util.List;

/**
 * Puerto primario de consulta del catalogo de asientos por recinto.
 * Expone una proyeccion inmutable lista para presentar sin filtrar entidades del dominio.
 */
public interface CatalogoUseCase {

    List<AsientoDeRecinto> listarAsientos(String recintoId);

    record AsientoDeRecinto(String id, String codigoUbicacion, String precio, String estado) {}
}
