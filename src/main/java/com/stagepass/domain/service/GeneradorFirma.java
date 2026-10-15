package com.stagepass.domain.service;

import com.stagepass.domain.model.Boleto;
import com.stagepass.domain.model.OrdenCompra;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Regla 4: firma SHA-256 de los datos inmutables de la orden mas la clave secreta del evento.
 * Si alguien edita el monto o la expiracion en la base, la firma deja de validar.
 */
public class GeneradorFirma {

    public String generar(OrdenCompra orden, String claveSecreta) {
        // Los campos van en orden fijo, nunca con toString() de un mapa: ese orden no esta
        // garantizado y la firma cambiaria entre corridas.
        String datos = String.join("|",
                orden.getId(),
                orden.getMontoTotal().toPlainString(),
                orden.getExpiraEn().toString(),
                claveSecreta);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(datos.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 esta garantizado por la plataforma Java; si falta, el runtime esta roto.
            throw new IllegalStateException("El runtime de Java no soporta SHA-256", e);
        }
    }

    /** El codigo se deriva del id del boleto; la sobrecarga por id evita construir un Boleto a medias. */
    public String generarCodigoRedencion(String boletoId) {
        return "SP-" + boletoId.substring(0, 8).toUpperCase(Locale.ROOT);
    }

    public String generarCodigoRedencion(Boleto boleto) {
        return generarCodigoRedencion(boleto.getId());
    }
}
