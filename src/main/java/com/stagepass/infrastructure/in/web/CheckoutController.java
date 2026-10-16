package com.stagepass.infrastructure.in.web;

import com.stagepass.ports.in.CheckoutUseCase;
import com.stagepass.ports.in.IniciarCompraResponse;
import com.stagepass.ports.in.SolicitudCompra;
import com.stagepass.domain.model.OrdenCompra;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador primario de compras. Su unico trabajo es traducir HTTP <-> puertos del
 * nucleo; no conoce marcas ni calculos (esas viven en el dominio por contrato).
 */
@RestController
@RequestMapping("/api/v1")
public class CheckoutController {

    private final CheckoutUseCase checkout;

    public CheckoutController(CheckoutUseCase checkout) {
        this.checkout = checkout;
    }

    /** Arranca la reserva: bloquea, calcula los tramos y devuelve ventana de 10 minutos. */
    @PostMapping("/compras")
    public ResponseEntity<IniciarCompraResponse> iniciarCompra(@Valid @RequestBody PeticionDeCompra peticion) {
        IniciarCompraResponse respuesta = checkout.iniciarCompra(
                new SolicitudCompra(peticion.eventoId(), peticion.clienteId(), peticion.asientosId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /** Revalida el paquete elegido frente a la orden bloqueada (montos y vigencia). */
    @PostMapping("/compras/{ordenId}/seleccion")
    public RespuestaDeSeleccion seleccionar(@PathVariable String ordenId,
                                            @Valid @RequestBody PeticionDeSeleccion peticion) {
        OrdenCompra orden = checkout.seleccionarAsientos(ordenId, peticion.asientosId());
        return RespuestaDeSeleccion.de(orden);
    }
}
