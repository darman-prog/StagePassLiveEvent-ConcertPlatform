package com.stagepass.infrastructure.out.invoicing;

import com.stagepass.ports.out.Comprobante;
import com.stagepass.ports.out.Factura;
import com.stagepass.ports.out.FacturaEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Mock del emisor de factura electronica DIAN. El numero de comprobante es determinista
 * a partir del id de la orden, para que la demo siempre muestre el mismo recibo.
 */
public class DianFacturaEmitter implements FacturaEmitter {

    private static final Logger LOG = LoggerFactory.getLogger(DianFacturaEmitter.class);

    @Override
    public Comprobante emitir(Factura factura) {
        String numero = "FAC-" + factura.ordenId().substring(0, 8).toUpperCase(Locale.ROOT);
        String url = "https://facturas.mock.dian.example/" + numero;
        LOG.info("[DIAN] factura {} emitida por {} a la orden {}", numero, factura.monto(), factura.ordenId());
        return new Comprobante(numero, url);
    }
}
