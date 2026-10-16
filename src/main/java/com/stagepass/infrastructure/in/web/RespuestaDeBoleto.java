package com.stagepass.infrastructure.in.web;

import com.stagepass.domain.model.Boleto;

/** Boleta emitida: el hash viaja completo para que el front pueda mostrarlo en la QR. */
public record RespuestaDeBoleto(
        String boletoId,
        String ordenId,
        String codigoRedencion,
        String firmaHash,
        String fechaEmision) {

    public static RespuestaDeBoleto de(Boleto boleto) {
        return new RespuestaDeBoleto(
                boleto.getId(),
                boleto.getOrdenId(),
                boleto.getCodigoRedencion(),
                boleto.getFirmaHash(),
                boleto.getFechaEmision().toString());
    }
}
