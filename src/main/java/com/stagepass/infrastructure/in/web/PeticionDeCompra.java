package com.stagepass.infrastructure.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Cuerpo del POST /compras. Los nombres de campo son el contrato del front. */
public record PeticionDeCompra(
        @NotBlank String eventoId,
        @NotBlank String clienteId,
        @NotEmpty List<String> asientosId) {}
