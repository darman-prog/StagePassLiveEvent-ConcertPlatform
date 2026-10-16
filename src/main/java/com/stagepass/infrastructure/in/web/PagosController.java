package com.stagepass.infrastructure.in.web;

import com.stagepass.domain.model.Boleto;
import com.stagepass.ports.in.ConfirmacionPago;
import com.stagepass.ports.in.NotificacionTransaccion;
import com.stagepass.ports.in.PagosUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

/**
 * Adaptador primario de pagos: la confirmacion activa que consulta la pasarela elegida
 * y el webhook asincronico donde las pasarelas reales tambien empujan avisos.
 */
@RestController
@RequestMapping("/api/v1")
public class PagosController {

    private final PagosUseCase pagos;

    public PagosController(PagosUseCase pagos) {
        this.pagos = pagos;
    }

    @PostMapping("/pagos/confirmar")
    public RespuestaDeBoleto confirmar(@Valid @RequestBody PeticionDeConfirmacion peticion) {
        // La pasarela viaja en minusculas para que el front envie Wompi, WOMPI o wompi igual.
        String pasarela = peticion.pasarela().toLowerCase(Locale.ROOT);
        Boleto boleto = pagos.confirmarPago(
                new ConfirmacionPago(peticion.ordenId(), pasarela, peticion.identificadorTransaccion()));
        return RespuestaDeBoleto.de(boleto);
    }

    @PostMapping("/pagos/webhook/{pasarela}")
    public Map<String, Boolean> webhook(@PathVariable String pasarela,
                                        @Valid @RequestBody PeticionDeWebhook peticion) {
        // El nucleo acepta el aviso (no-op documentado, el webhook no trae id de orden);
        // aqui se devuelve reconocimiento para que la pasarela no reintente.
        pagos.notificarCambioTransaccion(pasarela.toLowerCase(Locale.ROOT),
                new NotificacionTransaccion(peticion.identificadorTransaccion(), peticion.aprobado()));
        return Map.of("recibido", true);
    }
}
