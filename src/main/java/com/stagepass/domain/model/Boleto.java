package com.stagepass.domain.model;

import java.time.Instant;

/**
 * Boleta emitida solo cuando el pago fue verificado. La firma permite detectar
 * alteraciones posteriores de los datos de la orden.
 */
public class Boleto {

    private final String id;
    private final String ordenId;
    private final String codigoRedencion;
    private final String firmaHash;
    private final Instant fechaEmision;

    public Boleto(String id, String ordenId, String codigoRedencion,
                  String firmaHash, Instant fechaEmision) {
        this.id = id;
        this.ordenId = ordenId;
        this.codigoRedencion = codigoRedencion;
        this.firmaHash = firmaHash;
        this.fechaEmision = fechaEmision;
    }

    public String getId() {
        return id;
    }

    public String getOrdenId() {
        return ordenId;
    }

    public String getCodigoRedencion() {
        return codigoRedencion;
    }

    public String getFirmaHash() {
        return firmaHash;
    }

    public Instant getFechaEmision() {
        return fechaEmision;
    }
}
