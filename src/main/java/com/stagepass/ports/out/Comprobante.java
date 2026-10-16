package com.stagepass.ports.out;

/** Respuesta de la autoridad de facturacion: numero y enlace de verificacion. */
public record Comprobante(String numero, String url) {}
