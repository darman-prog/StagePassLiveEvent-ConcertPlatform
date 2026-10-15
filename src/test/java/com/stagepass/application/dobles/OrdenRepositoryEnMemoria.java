package com.stagepass.application.dobles;

import com.stagepass.domain.model.EstadoOrden;
import com.stagepass.domain.model.OrdenCompra;
import com.stagepass.ports.out.OrdenRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Almacen de ordenes de mentira, con las mismas consultas que promete el puerto. */
public class OrdenRepositoryEnMemoria implements OrdenRepository {

    private final Map<String, OrdenCompra> porId = new LinkedHashMap<>();

    @Override
    public void guardar(OrdenCompra orden) {
        porId.put(orden.getId(), orden);
    }

    @Override
    public OrdenCompra buscarPorId(String ordenId) {
        return porId.get(ordenId);
    }

    @Override
    public List<OrdenCompra> buscarActivasPorClienteYEvento(String clienteId, String eventoId) {
        // Activas = pendientes de pago o aprobadas: las vencidas y rechazadas ya no retienen cupo.
        return porId.values().stream()
                .filter(orden -> orden.getCliente().getId().equals(clienteId)
                        && orden.getEventoId().equals(eventoId)
                        && (orden.getEstado() == EstadoOrden.PENDIENTE_PAGO
                                || orden.getEstado() == EstadoOrden.APROBADA))
                .toList();
    }

    @Override
    public List<OrdenCompra> buscarVencidas(Instant ahora) {
        return porId.values().stream()
                .filter(orden -> orden.expiro(ahora))
                .toList();
    }
}
