package com.stagepass.ports.in;

/** Respuesta del checkout: la orden creada, su codigo de pago y el vencimiento de la ventana. */
public record IniciarCompraResponse(String ordenId, String codigoPago, String expiraEn) {}
