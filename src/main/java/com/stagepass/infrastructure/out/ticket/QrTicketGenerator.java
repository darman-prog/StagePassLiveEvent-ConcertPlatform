package com.stagepass.infrastructure.out.ticket;

import com.stagepass.ports.out.RepresentacionBoleto;
import com.stagepass.ports.out.TicketGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

/**
 * Mock del generador grafico del boleto. La firma SHA-256 la calcula el dominio; aqui
 * solo se produce la representacion: dibuja una matriz pseudo-QR determinista a partir
 * del hash de la boleto y la deja en target/qr/ para poder mostrarla en la defensa.
 */
public class QrTicketGenerator implements TicketGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(QrTicketGenerator.class);

    // 21x21 modulos es el tamanio minimo real de un QR; cada celda de 4 px lo hace legible.
    private static final int MODULOS = 21;
    private static final int CELDA = 4;

    @Override
    public RepresentacionBoleto generar(String codigo, String firmaHash, String url) {
        Path carpeta = Path.of("target", "qr");
        Path archivo = carpeta.resolve(codigo + ".png");
        if (Files.notExists(archivo)) {
            dibujar(archivo, firmaHash);
        }
        LOG.info("[QR] representacion {} escrita en {}", codigo, archivo);
        return new RepresentacionBoleto(codigo, firmaHash, url);
    }

    private void dibujar(Path archivo, String firmaHash) {
        try {
            Files.createDirectories(archivo.getParent());
            BufferedImage imagen = new BufferedImage(MODULOS * CELDA, MODULOS * CELDA, BufferedImage.TYPE_INT_RGB);
            byte[] hash = HexFormat.of().parseHex(firmaHash);

            // Matriz determinista: cada celda sale de un bit del hash, asi el dibujo es
            // estable para una misma boleto y distinto entre boletas.
            for (int fila = 0; fila < MODULOS; fila++) {
                for (int columna = 0; columna < MODULOS; columna++) {
                    int posicion = fila * MODULOS + columna;
                    boolean oscuro = ((hash[posicion % hash.length] >>> (posicion % 8)) & 1) == 1;
                    pintarCelda(imagen, fila, columna, oscuro);
                }
            }
            ImageIO.write(imagen, "png", archivo.toFile());
        } catch (IOException e) {
            // El dibujo es accesorio: si falla, el boleto siguen siendo valido sin imagen.
            LOG.warn("[QR] no se pudo escribir la imagen {}: {}", archivo, e.getMessage());
        }
    }

    private void pintarCelda(BufferedImage imagen, int fila, int columna, boolean oscuro) {
        if (!oscuro) {
            return;
        }
        for (int y = fila * CELDA; y < (fila + 1) * CELDA; y++) {
            for (int x = columna * CELDA; x < (columna + 1) * CELDA; x++) {
                imagen.setRGB(x, y, 0x000000);
            }
        }
    }
}
