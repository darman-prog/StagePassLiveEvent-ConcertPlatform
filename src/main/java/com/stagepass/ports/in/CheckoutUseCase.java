package com.stagepass.ports.in;

import com.stagepass.domain.model.OrdenCompra;

import java.util.List;

/** Puerto primario del flujo de compra: iniciar la orden y consultar su seleccion de asientos. */
public interface CheckoutUseCase {

    IniciarCompraResponse iniciarCompra(SolicitudCompra solicitud);

    OrdenCompra seleccionarAsientos(String ordenId, List<String> asientosId);
}
