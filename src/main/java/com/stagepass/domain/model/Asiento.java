package com.stagepass.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un asiento del recinto. Cambiar de DISPONIBLE a VENDIDO pasa siempre por BLOQUEADO:
 * saltarse el estado intermedio permitiria que dos personas compren la misma silla.
 */
public class Asiento {

    private final String id;
    private final String recintoId;
    private final String codigoUbicacion;
    private final BigDecimal precioBase;
    private EstadoAsiento estado;
    private Instant bloqueadoHasta;

    public Asiento(String id, String recintoId, String codigoUbicacion, BigDecimal precioBase) {
        this.id = id;
        this.recintoId = recintoId;
        this.codigoUbicacion = codigoUbicacion;
        this.precioBase = precioBase;
        this.estado = EstadoAsiento.DISPONIBLE;
    }

    public void bloquear(Instant hasta) {
        this.estado = EstadoAsiento.BLOQUEADO;
        this.bloqueadoHasta = hasta;
    }

    public void vender() {
        this.estado = EstadoAsiento.VENDIDO;
        this.bloqueadoHasta = null;
    }

    public void liberar() {
        this.estado = EstadoAsiento.DISPONIBLE;
        this.bloqueadoHasta = null;
    }

    /** True si el bloqueo temporal ya vencio y hay que devolver el asiento al pool. */
    public boolean bloqueoVencido(Instant ahora) {
        return estado == EstadoAsiento.BLOQUEADO
                && bloqueadoHasta != null
                && ahora.isAfter(bloqueadoHasta);
    }

    public boolean estaDisponible() {
        return estado == EstadoAsiento.DISPONIBLE;
    }

    public String getId() {
        return id;
    }

    public String getRecintoId() {
        return recintoId;
    }

    public String getCodigoUbicacion() {
        return codigoUbicacion;
    }

    public BigDecimal getPrecioBase() {
        return precioBase;
    }

    public EstadoAsiento getEstado() {
        return estado;
    }

    public Instant getBloqueadoHasta() {
        return bloqueadoHasta;
    }
}
