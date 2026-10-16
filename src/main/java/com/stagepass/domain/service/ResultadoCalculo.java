package com.stagepass.domain.service;

import java.math.BigDecimal;

/** Los tres numeros que la regla de tramos entrega a la OrdenCompra. Inmutable. */
public record ResultadoCalculo(BigDecimal tarifaServicio, BigDecimal descuento, BigDecimal montoTotal) {
}
