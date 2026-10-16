package com.stagepass.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Reserva de asientos con ventana de pago de 10 minutos. El estado solo se mueve con
 * aprobar/rechazar/expirar; el monto y la expiracion son datos inmutables de la orden.
 */
public class OrdenCompra {

    private final String id;
    private final String eventoId;
    private final Cliente cliente;
    private final List<Asiento> asientos;
    private final BigDecimal tarifaServicio;
    private final BigDecimal descuentoAplicado;
    private final BigDecimal montoTotal;
    private final Instant expiraEn;
    private EstadoOrden estado;

    public OrdenCompra(String id, String eventoId, Cliente cliente, List<Asiento> asientos,
                       BigDecimal tarifaServicio, BigDecimal descuentoAplicado,
                       BigDecimal montoTotal, Instant expiraEn) {
        this.id = id;
        this.eventoId = eventoId;
        this.cliente = cliente;
        // Copia inmutable: nadie modifica los asientos de la orden desde afuera.
        this.asientos = List.copyOf(asientos);
        this.tarifaServicio = tarifaServicio;
        this.descuentoAplicado = descuentoAplicado;
        this.montoTotal = montoTotal;
        this.expiraEn = expiraEn;
        this.estado = EstadoOrden.PENDIENTE_PAGO;
    }

    public void aprobar() {
        this.estado = EstadoOrden.APROBADA;
    }

    public void rechazar() {
        this.estado = EstadoOrden.RECHAZADA;
    }

    public void expirar() {
        this.estado = EstadoOrden.EXPIRADA;
    }

    /** True si la orden sigue pendiente y su ventana de pago ya paso. */
    public boolean expiro(Instant ahora) {
        return estado == EstadoOrden.PENDIENTE_PAGO && ahora.isAfter(expiraEn);
    }

    public String getId() {
        return id;
    }

    public String getEventoId() {
        return eventoId;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public List<Asiento> getAsientos() {
        return asientos;
    }

    public BigDecimal getTarifaServicio() {
        return tarifaServicio;
    }

    public BigDecimal getDescuentoAplicado() {
        return descuentoAplicado;
    }

    public BigDecimal getMontoTotal() {
        return montoTotal;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public EstadoOrden getEstado() {
        return estado;
    }
}
