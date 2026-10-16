package com.stagepass.infrastructure.out.payment;

/** Mock de la pasarela PayPal, segunda implementacion del mismo puerto PaymentGateway. */
public class PayPalPaymentGateway extends PasarelaMock {

    public PayPalPaymentGateway() {
        super("paypal");
    }
}
