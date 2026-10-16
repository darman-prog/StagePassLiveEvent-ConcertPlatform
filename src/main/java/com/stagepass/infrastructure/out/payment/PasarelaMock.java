package com.stagepass.infrastructure.out.payment;

import com.stagepass.ports.out.PaymentGateway;

import java.util.UUID;

/**
 * Simulacro compartido de las tres pasarelas del taller. Es determinista: la decision
 * depende solo del prefijo del identificador de transaccion, para que los tests y la
 * demo de la defensa puedan elegir el desenlace sin red ni credenciales.
 *
 * Convencion acordada: "OK-*" o "APROBADA-*" llegan APROBADOS, "RECHAZADA-*" llegan
 * RECHAZADOS y cualquier otro identificador queda PENDIENTE.
 */
public abstract class PasarelaMock implements PaymentGateway {

    private final String nombre;

    protected PasarelaMock(String nombre) {
        this.nombre = nombre;
    }

    @Override
    public ReferenciaPago registrarPago(SolicitudPago solicitud) {
        // El nucleo no lo invoca hoy: queda listo para cuando el front inicie el pago en la pasarela.
        return new ReferenciaPago(nombre, "TX-" + UUID.randomUUID());
    }

    @Override
    public EstadoPago consultarEstado(ReferenciaPago referencia) {
        String transaccion = referencia.identificadorTransaccion();
        if (transaccion.startsWith("OK-") || transaccion.startsWith("APROBADA-")) {
            return EstadoPago.APROBADO;
        }
        if (transaccion.startsWith("RECHAZADA-")) {
            return EstadoPago.RECHAZADO;
        }
        return EstadoPago.PENDIENTE;
    }
}
