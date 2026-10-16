package com.stagepass.infrastructure.out.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Los instantes viajan como String ISO-8601 SIEMPRE con nueve digitos de nanos y fija
 * la zona Z. Con ancho fijo, el texto es determinista y es exactamente lo que la firma
 * del boleto recibe como dato de entrada, asi el hash sobrevive el viaje por el disco.
 */
public final class InstantTexto {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'")
            .withZone(ZoneOffset.UTC);

    private InstantTexto() {
    }

    public static String formatear(Instant valor) {
        return FORMATO.format(valor);
    }

    public static Instant aInstant(String texto) {
        // Instant.parse entiende por defecto 0, 3, 6 o 9 digitos de fraccion.
        return Instant.parse(texto);
    }
}
