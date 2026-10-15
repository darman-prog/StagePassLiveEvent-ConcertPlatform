package com.stagepass.ports.out;

import com.stagepass.domain.model.OrdenCompra;

import java.time.Instant;
import java.util.List;

/** Lo que el nucleo necesita del almacen de ordenes; siempre entidades, nunca documentos. */
public interface OrdenRepository {

    void guardar(OrdenCompra orden);

    OrdenCompra buscarPorId(String ordenId);

    /**
     * Ordenes que retienen cupo para el tope de 6 boletas: pendientes de pago y
     * aprobadas (las vencidas y rechazadas ya no cuentan). Sin este metodo la
     * regla de las 6 boletas no se puede cumplir.
     */
    List<OrdenCompra> buscarActivasPorClienteYEvento(String clienteId, String eventoId);

    /** Ordenes pendientes de pago cuya ventana de 10 minutos ya vencio. */
    List<OrdenCompra> buscarVencidas(Instant ahora);
}
