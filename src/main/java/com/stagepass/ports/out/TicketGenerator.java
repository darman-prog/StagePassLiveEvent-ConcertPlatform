package com.stagepass.ports.out;

/**
 * Representacion grafica del boleto (el QR del taller). La firma SHA-256 la calcula el
 * dominio; a este adaptador solo le toca producir la imagen o el enlace verificable.
 */
public interface TicketGenerator {

    RepresentacionBoleto generar(String codigo, String firmaHash, String url);
}
