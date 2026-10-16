package com.stagepass.ports.out;

/** Formato final del boleto para el comprador: codigo, firma y enlace o QR. */
public record RepresentacionBoleto(String codigo, String firmaHash, String url) {}
