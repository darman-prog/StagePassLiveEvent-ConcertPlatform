package com.stagepass.ports.out;

import java.math.BigDecimal;

/**
 * Un solo puerto para las tres pasarelas: PayPal, Wompi y PSE son implementaciones
 * intercambiables y el dominio nunca menciona a ninguna por nombre. La implementacion
 * concreta se elige por el campo pasarela de la referencia.
 */
public interface PaymentGateway {

    ReferenciaPago registrarPago(SolicitudPago solicitud);

    EstadoPago consultarEstado(ReferenciaPago referencia);

    /** Datos minimos para pedirle a una pasarela el inicio de un pago. */
    record SolicitudPago(String ordenId, BigDecimal monto, String correoComprador) {}

    /** Con que pasarela y numero de transaccion quedo registrado el pago. */
    record ReferenciaPago(String pasarela, String identificadorTransaccion) {}

    enum EstadoPago { PENDIENTE, APROBADO, RECHAZADO }
}
