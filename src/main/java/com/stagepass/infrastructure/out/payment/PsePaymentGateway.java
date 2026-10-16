package com.stagepass.infrastructure.out.payment;

/** Mock de la pasarela PSE (botones de bancos), tercera implementacion del puerto. */
public class PsePaymentGateway extends PasarelaMock {

    public PsePaymentGateway() {
        super("pse");
    }
}
