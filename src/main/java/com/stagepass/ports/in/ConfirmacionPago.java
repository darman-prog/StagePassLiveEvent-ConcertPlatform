package com.stagepass.ports.in;

/** Datos del pago que el comprador ya inicio en la pasarela elegida. */
public record ConfirmacionPago(String ordenId, String pasarela, String identificadorTransaccion) {}
