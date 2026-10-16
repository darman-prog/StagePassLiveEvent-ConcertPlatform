package com.stagepass.infrastructure.in.web;

import com.stagepass.domain.model.OrdenCompra;

import java.util.List;

/** Resumen de la orden que el checkout revalida montos y vencimiento antes de cobrar. */
public record RespuestaDeSeleccion(
        String ordenId,
        String eventoId,
        String estado,
        String expiraEn,
        List<AsientoDeRespuesta> asientos,
        String tarifaServicio,
        String descuentoAplicado,
        String montoTotal) {

    public record AsientoDeRespuesta(String id, String codigoUbicacion, String precio) {}

    /** Traduce la entidad del dominio a JSON; el monto de la orden viaja con dos decimales. */
    public static RespuestaDeSeleccion de(OrdenCompra orden) {
        return new RespuestaDeSeleccion(
                orden.getId(),
                orden.getEventoId(),
                orden.getEstado().name(),
                orden.getExpiraEn().toString(),
                orden.getAsientos().stream()
                        .map(asiento -> new AsientoDeRespuesta(
                                asiento.getId(),
                                asiento.getCodigoUbicacion(),
                                asiento.getPrecioBase().toPlainString()))
                        .toList(),
                orden.getTarifaServicio().toPlainString(),
                orden.getDescuentoAplicado().toPlainString(),
                orden.getMontoTotal().toPlainString());
    }
}
