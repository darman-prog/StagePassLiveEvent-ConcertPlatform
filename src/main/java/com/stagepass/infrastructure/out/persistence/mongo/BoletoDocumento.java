package com.stagepass.infrastructure.out.persistence.mongo;

/**
 * Documento tecnico de una boleta emitida en la coleccion "boletos". El codigo de
 * redencion lleva indice unico: un webhook reentregado jamas crea un boleto doble.
 */
public record BoletoDocumento(
        String id,
        String ordenId,
        String codigoRedencion,
        String firmaHash,
        String fechaEmision) {}
