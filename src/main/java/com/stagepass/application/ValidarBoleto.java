package com.stagepass.application;

import com.stagepass.domain.model.Boleto;
import com.stagepass.domain.model.OrdenCompra;
import com.stagepass.domain.service.GeneradorFirma;
import com.stagepass.ports.in.ResultadoValidacion;
import com.stagepass.ports.in.ValidacionUseCase;
import com.stagepass.ports.out.BoletoRepository;
import com.stagepass.ports.out.OrdenRepository;

/**
 * Validacion en la puerta: recalcula la firma del boleto con los datos actuales de la
 * orden y la compara con la guardada, para detectar boletos alterados o inventados.
 */
public class ValidarBoleto implements ValidacionUseCase {

    private final BoletoRepository boletos;
    private final OrdenRepository ordenes;
    private final GeneradorFirma generadorFirma;
    private final String claveSecretaEventos;

    public ValidarBoleto(BoletoRepository boletos, OrdenRepository ordenes,
                         GeneradorFirma generadorFirma, String claveSecretaEventos) {
        this.boletos = boletos;
        this.ordenes = ordenes;
        this.generadorFirma = generadorFirma;
        this.claveSecretaEventos = claveSecretaEventos;
    }

    @Override
    public ResultadoValidacion validarBoleto(String codigoRedencion) {

        Boleto boleto = boletos.buscarPorCodigo(codigoRedencion);
        if (boleto == null) {
            return new ResultadoValidacion(false, "El codigo %s no existe".formatted(codigoRedencion));
        }

        OrdenCompra orden = ordenes.buscarPorId(boleto.getOrdenId());
        if (orden == null) {
            return new ResultadoValidacion(false,
                    "La orden %s del boleto no existe".formatted(boleto.getOrdenId()));
        }

        // Se recalcula la firma: si el monto o la expiracion de la orden fueron alterados,
        // el hash no coincide y el boleto se rechaza.
        String firmaEsperada = generadorFirma.generar(orden, claveSecretaEventos);
        if (!firmaEsperada.equals(boleto.getFirmaHash())) {
            return new ResultadoValidacion(false, "La firma no coincide: el boleto fue alterado");
        }

        return new ResultadoValidacion(true, "Boleto valido");
    }
}
