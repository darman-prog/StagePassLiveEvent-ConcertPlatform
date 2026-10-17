package com.stagepass.infrastructure.in.web;

import com.stagepass.ports.in.CatalogoUseCase;
import com.stagepass.ports.in.CatalogoUseCase.AsientoDeRecinto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adaptador primario REST para consultar el mapa de asientos de un recinto en vivo.
 */
@RestController
@RequestMapping("/api/v1")
public class CatalogoController {

    private final CatalogoUseCase catalogo;

    public CatalogoController(CatalogoUseCase catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping("/recintos/{recintoId}/asientos")
    public List<AsientoDeRecinto> listarAsientos(@PathVariable String recintoId) {
        return catalogo.listarAsientos(recintoId);
    }
}
