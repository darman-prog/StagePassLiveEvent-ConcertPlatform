package com.stagepass.ports.in;

import com.stagepass.domain.model.Boleto;

/** Puerto primario del flujo de pago: confirmacion ante la pasarela y aviso de cambios. */
public interface PagosUseCase {

    Boleto confirmarPago(ConfirmacionPago confirmacion);

    /**
     * La pasarela avisa por webhook que una transaccion cambio de estado. El aviso no
     * trae el id de la orden, asi que el nucleo no puede mover datos con el; el empuje
     * en vivo a la vista lo resuelve el adaptador web.
     */
    void notificarCambioTransaccion(String pasarela, NotificacionTransaccion notificacion);
}
