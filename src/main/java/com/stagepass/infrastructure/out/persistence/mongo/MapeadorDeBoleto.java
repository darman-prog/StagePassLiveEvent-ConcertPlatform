package com.stagepass.infrastructure.out.persistence.mongo;

import com.stagepass.domain.model.Boleto;
import com.stagepass.infrastructure.out.persistence.InstantTexto;

/**
 * Traduce entre la entidad Boleto del dominio y su documento en la coleccion "boletos".
 */
public final class MapeadorDeBoleto {

    private MapeadorDeBoleto() {
    }

    public static BoletoDocumento aDocumento(Boleto boleto) {
        return new BoletoDocumento(
                boleto.getId(),
                boleto.getOrdenId(),
                boleto.getCodigoRedencion(),
                boleto.getFirmaHash(),
                InstantTexto.formatear(boleto.getFechaEmision()));
    }

    public static Boleto aEntidad(BoletoDocumento documento) {
        return new Boleto(
                documento.id(),
                documento.ordenId(),
                documento.codigoRedencion(),
                documento.firmaHash(),
                InstantTexto.aInstant(documento.fechaEmision()));
    }
}
