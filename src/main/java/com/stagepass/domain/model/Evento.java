package com.stagepass.domain.model;

import java.time.LocalDateTime;

/**
 * Funcion o evento programado en un recinto. Nace en PROGRAMADO y solo cambia de estado
 * por sus propios metodos de negocio, nunca con un setter libre.
 */
public class Evento {

    private final String id;
    private final String nombre;
    private final TipoEvento tipo;
    private final String recintoId;
    private final LocalDateTime fechaHora;
    private EstadoEvento estado;

    public Evento(String id, String nombre, TipoEvento tipo, String recintoId, LocalDateTime fechaHora) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = tipo;
        this.recintoId = recintoId;
        this.fechaHora = fechaHora;
        this.estado = EstadoEvento.PROGRAMADO;
    }

    public void cancelar() {
        this.estado = EstadoEvento.CANCELADO;
    }

    public void marcarAgotado() {
        this.estado = EstadoEvento.AGOTADO;
    }

    public String getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoEvento getTipo() {
        return tipo;
    }

    public String getRecintoId() {
        return recintoId;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public EstadoEvento getEstado() {
        return estado;
    }
}
