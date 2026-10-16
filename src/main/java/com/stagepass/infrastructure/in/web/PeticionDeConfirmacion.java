package com.stagepass.infrastructure.in.web;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo del POST /pagos/confirmar: el front declara la pasarela y la transaccion. */
public record PeticionDeConfirmacion(
        @NotBlank String ordenId,
        @NotBlank String pasarela,
        @NotBlank String identificadorTransaccion) {}
