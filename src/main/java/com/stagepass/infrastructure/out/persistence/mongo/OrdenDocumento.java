package com.stagepass.infrastructure.out.persistence.mongo;

import java.util.List;

/**
 * Documento tecnico de una orden de compra en la coleccion "ordenes". Es un formato
 * interno del adaptador: el dominio nunca lo ve (el mapeador lo convierte a ambos lados).
 * Los montos e instantes viajan como texto para conservar precision exacta.
 */
public record OrdenDocumento(
        String id,
        String eventoId,
        ClienteDeOrden cliente,
        List<AsientoDeOrden> asientos,
        String tarifaServicio,
        String descuentoAplicado,
        String montoTotal,
        String expiraEn,
        String estado) {

    public record ClienteDeOrden(
            String id,
            String nombreCompleto,
            String correoElectronico,
            String telefonoMovil,
            String nacionalidad) {}

    public record AsientoDeOrden(
            String id,
            String recintoId,
            String codigoUbicacion,
            String precioBase,
            String estado,
            String bloqueadoHasta) {}
}
