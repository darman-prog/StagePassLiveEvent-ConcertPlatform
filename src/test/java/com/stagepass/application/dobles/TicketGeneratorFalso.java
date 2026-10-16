package com.stagepass.application.dobles;

import com.stagepass.ports.out.RepresentacionBoleto;
import com.stagepass.ports.out.TicketGenerator;

/** Generador de QR de mentira: devuelve la representacion con los datos que recibe. */
public class TicketGeneratorFalso implements TicketGenerator {

    @Override
    public RepresentacionBoleto generar(String codigo, String firmaHash, String url) {
        return new RepresentacionBoleto(codigo, firmaHash, url);
    }
}
