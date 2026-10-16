package com.stagepass.infrastructure.in.web;

import com.stagepass.ports.in.ResultadoValidacion;
import com.stagepass.ports.in.ValidacionUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador primario de validacion en puerta: la URL que imprime el QR apunta aqui,
 * de modo que escanear el codigo responde si el boleto es real y no fue alterado.
 */
@RestController
@RequestMapping("/api/v1")
public class ValidacionController {

    private final ValidacionUseCase validacion;

    public ValidacionController(ValidacionUseCase validacion) {
        this.validacion = validacion;
    }

    @GetMapping("/boletos/{codigo}")
    public ResultadoValidacion validar(@PathVariable String codigo) {
        return validacion.validarBoleto(codigo);
    }
}
