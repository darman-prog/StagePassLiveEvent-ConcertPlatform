package com.stagepass.ports.out;

import com.stagepass.domain.model.Boleto;

/** Emision y busqueda de boletos por su codigo de redencion. */
public interface BoletoRepository {

    void guardar(Boleto boleto);

    Boleto buscarPorCodigo(String codigoRedencion);
}
