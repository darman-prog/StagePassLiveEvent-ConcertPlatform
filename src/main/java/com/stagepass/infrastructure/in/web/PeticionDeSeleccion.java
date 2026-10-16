package com.stagepass.infrastructure.in.web;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Cuerpo del POST /compras/{ordenId}/seleccion: el paquete de sillas a revalidar. */
public record PeticionDeSeleccion(@NotEmpty List<String> asientosId) {}
