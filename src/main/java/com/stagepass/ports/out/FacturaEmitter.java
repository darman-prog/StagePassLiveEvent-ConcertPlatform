package com.stagepass.ports.out;

/** Emision de la factura electronica (adaptador DIAN del taller). */
public interface FacturaEmitter {

    Comprobante emitir(Factura factura);
}
