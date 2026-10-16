package com.stagepass.infrastructure.in.web;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo del webhook asincronico que las pasarelas llamarian cuando cambia el estado. */
public record PeticionDeWebhook(
        @NotBlank String identificadorTransaccion,
        boolean aprobado) {}
