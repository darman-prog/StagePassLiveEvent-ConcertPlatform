package com.stagepass.application.dobles;

import com.stagepass.ports.out.PaymentGateway;

/**
 * Pasarela de mentira: representa una cuenta concreta (por eso lleva su nombre) y
 * responde siempre el estado con el que se creo.
 */
public class PaymentGatewayFalso implements PaymentGateway {

    private final String pasarela;
    private final EstadoPago estado;

    public PaymentGatewayFalso(String pasarela, EstadoPago estado) {
        this.pasarela = pasarela;
        this.estado = estado;
    }

    @Override
    public ReferenciaPago registrarPago(SolicitudPago solicitud) {
        return new ReferenciaPago(pasarela, "tx-" + solicitud.ordenId());
    }

    @Override
    public EstadoPago consultarEstado(ReferenciaPago referencia) {
        return estado;
    }
}
