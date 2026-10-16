package com.stagepass.ports.out;

import java.math.BigDecimal;

/** Datos de la factura que se emite tras un pago aprobado. */
public record Factura(String ordenId, String clienteId, BigDecimal monto) {}
