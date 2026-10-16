package com.stagepass.application.dobles;

import com.stagepass.ports.out.Comprobante;
import com.stagepass.ports.out.Factura;
import com.stagepass.ports.out.FacturaEmitter;

/** Emisor de facturas de mentira: recuerda la ultima factura y devuelve un comprobante fijo. */
public class FacturaEmitterFalso implements FacturaEmitter {

    private Factura ultima;

    @Override
    public Comprobante emitir(Factura factura) {
        this.ultima = factura;
        return new Comprobante("DIAN-0001", "https://dian.test/comprobante/0001");
    }

    public Factura ultima() {
        return ultima;
    }
}
