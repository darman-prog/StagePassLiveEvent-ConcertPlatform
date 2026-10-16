package com.stagepass.application.dobles;

import com.stagepass.domain.model.Boleto;
import com.stagepass.ports.out.BoletoRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/** Almacen de boletos de mentira, con busqueda por codigo de redencion. */
public class BoletoRepositoryEnMemoria implements BoletoRepository {

    private final Map<String, Boleto> porCodigo = new LinkedHashMap<>();

    @Override
    public void guardar(Boleto boleto) {
        porCodigo.put(boleto.getCodigoRedencion(), boleto);
    }

    @Override
    public Boleto buscarPorCodigo(String codigoRedencion) {
        return porCodigo.get(codigoRedencion);
    }

    public int cantidad() {
        return porCodigo.size();
    }
}
