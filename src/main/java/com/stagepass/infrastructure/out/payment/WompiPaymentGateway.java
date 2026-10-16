package com.stagepass.infrastructure.out.payment;

/**
 * Mock de la pasarela colombiana Wompi. Un solo puerto PaymentGateway para todas: el
 * dominio nunca menciona a Wompi por nombre, solo recibe el bean como implementacion.
 */
public class WompiPaymentGateway extends PasarelaMock {

    public WompiPaymentGateway() {
        super("wompi");
    }
}
