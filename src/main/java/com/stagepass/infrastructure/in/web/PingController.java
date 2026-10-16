package com.stagepass.infrastructure.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador primario de humo (smoke test): unico trabajo es confirmar que la aplicacion
 * arranca y sirve HTTP. No conoce reglas de negocio; los controllers reales del taller
 * (compras, webhook de pagos) viviran en este mismo paquete cuando existan los casos de uso.
 */
@RestController
public class PingController {

    /** Contrato acordado para el dia 0 del taller: GET /ping responde 200 con cuerpo "pong". */
    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
