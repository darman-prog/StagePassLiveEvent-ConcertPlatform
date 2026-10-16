package com.stagepass.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Regla 1 del enunciado: tarifa administrativa y descuento por volumen.
 * Es una funcion pura: mismos datos de entrada, mismo resultado siempre.
 */
public class CalculadoraTarifaria {

    private static final BigDecimal TARIFA_BASE = new BigDecimal("0.10");
    private static final BigDecimal TARIFA_MEDIA = new BigDecimal("0.08");
    private static final BigDecimal TARIFA_CORPORATIVA = new BigDecimal("0.05");
    private static final BigDecimal DESCUENTO_MEDIO = new BigDecimal("0.05");
    private static final BigDecimal DESCUENTO_CORPORATIVO = new BigDecimal("0.10");

    public ResultadoCalculo calcular(int cantidadBoletos, BigDecimal subtotal) {

        if (cantidadBoletos <= 0) {
            throw new IllegalArgumentException("La cantidad de boletos debe ser positiva");
        }
        if (subtotal.signum() < 0) {
            throw new IllegalArgumentException("El subtotal no puede ser negativo");
        }

        BigDecimal tarifaServicio;
        BigDecimal descuento;

        if (cantidadBoletos <= 2) {
            // 1 a 2 boletas: tarifa 10% sin descuento. Es el tramo base.
            tarifaServicio = subtotal.multiply(TARIFA_BASE);
            descuento = BigDecimal.ZERO;
        } else if (cantidadBoletos <= 5) {
            // 3 a 5: la tarifa baja a 8% y aparece el descuento comercial del 5%.
            tarifaServicio = subtotal.multiply(TARIFA_MEDIA);
            descuento = subtotal.multiply(DESCUENTO_MEDIO);
        } else {
            // 6 o mas: tarifa administrativa fija del 5% y descuento del 10%.
            tarifaServicio = subtotal.multiply(TARIFA_CORPORATIVA);
            descuento = subtotal.multiply(DESCUENTO_CORPORATIVO);
        }

        BigDecimal montoTotal = subtotal.add(tarifaServicio).subtract(descuento);

        return new ResultadoCalculo(redondear(tarifaServicio), redondear(descuento), redondear(montoTotal));
    }

    /**
     * El dinero se redondea a dos decimales en un solo punto del sistema: si cada
     * adaptador redondea por su lado, las sumas no cuadran al final.
     */
    private BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
