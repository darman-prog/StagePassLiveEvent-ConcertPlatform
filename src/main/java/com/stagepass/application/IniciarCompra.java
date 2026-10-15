package com.stagepass.application;

import com.stagepass.domain.exception.AsientoNoDisponibleException;
import com.stagepass.domain.exception.ExcepcionNegocio;
import com.stagepass.domain.exception.OrdenExpiradaException;
import com.stagepass.domain.model.Asiento;
import com.stagepass.domain.model.Cliente;
import com.stagepass.domain.model.OrdenCompra;
import com.stagepass.domain.service.CalculadoraTarifaria;
import com.stagepass.domain.service.PoliticaExpiracion;
import com.stagepass.domain.service.ValidadorLimites;
import com.stagepass.ports.in.CheckoutUseCase;
import com.stagepass.ports.in.IniciarCompraResponse;
import com.stagepass.ports.in.SolicitudCompra;
import com.stagepass.ports.out.AsientoRepository;
import com.stagepass.ports.out.ClienteRepository;
import com.stagepass.ports.out.OrdenRepository;
import com.stagepass.ports.out.Reloj;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Caso de uso del checkout: valida la seleccion, aplica la tarifa por volumen, bloquea
 * las sillas y deja la orden pendiente de pago con su ventana de 10 minutos.
 */
public class IniciarCompra implements CheckoutUseCase {

    private final AsientoRepository asientos;
    private final OrdenRepository ordenes;
    private final ClienteRepository clientes;
    private final CalculadoraTarifaria calculadora;
    private final ValidadorLimites validadorLimites;
    private final PoliticaExpiracion politicaExpiracion;
    private final Reloj reloj;

    public IniciarCompra(AsientoRepository asientos, OrdenRepository ordenes, ClienteRepository clientes,
                         CalculadoraTarifaria calculadora, ValidadorLimites validadorLimites,
                         PoliticaExpiracion politicaExpiracion, Reloj reloj) {
        this.asientos = asientos;
        this.ordenes = ordenes;
        this.clientes = clientes;
        this.calculadora = calculadora;
        this.validadorLimites = validadorLimites;
        this.politicaExpiracion = politicaExpiracion;
        this.reloj = reloj;
    }

    @Override
    public IniciarCompraResponse iniciarCompra(SolicitudCompra solicitud) {

        Instant ahora = reloj.ahora();

        Cliente cliente = clientes.buscarPorId(solicitud.clienteId());
        // Sin el cliente no hay a quien facturar ni notificar: la compra no puede seguir.
        if (cliente == null) {
            throw new ExcepcionNegocio("El cliente %s no existe".formatted(solicitud.clienteId()));
        }

        List<Asiento> seleccionados = solicitud.asientosId().stream()
                .map(asientos::buscarPorId)
                .toList();

        // Si un solo asiento no esta libre se cae la compra completa: el cliente quiere
        // ese paquete de sillas, no una seleccion a medias.
        for (Asiento asiento : seleccionados) {
            if (asiento == null) {
                throw new ExcepcionNegocio("Uno de los asientos solicitados no existe");
            }
            if (!asiento.estaDisponible()) {
                throw new AsientoNoDisponibleException(
                        "El asiento %s no esta disponible".formatted(asiento.getCodigoUbicacion()));
            }
        }

        List<OrdenCompra> previas = ordenes.buscarActivasPorClienteYEvento(solicitud.clienteId(), solicitud.eventoId());
        int yaAdquiridas = previas.stream().mapToInt(orden -> orden.getAsientos().size()).sum();
        validadorLimites.validarCompra(solicitud.clienteId(), solicitud.eventoId(), yaAdquiridas, seleccionados.size());

        BigDecimal subtotal = seleccionados.stream()
                .map(Asiento::getPrecioBase)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var resultado = calculadora.calcular(seleccionados.size(), subtotal);

        String ordenId = UUID.randomUUID().toString();
        Instant vencimiento = politicaExpiracion.calcularVencimiento(ahora);

        OrdenCompra orden = new OrdenCompra(ordenId, solicitud.eventoId(), cliente, seleccionados,
                resultado.tarifaServicio(), resultado.descuento(), resultado.montoTotal(), vencimiento);

        // El bloqueo de las sillas y el guardado de la orden viajan juntos: el adaptador de
        // persistencia los resuelve en una transaccion para que no quede silla bloqueada sin orden.
        asientos.bloquear(solicitud.asientosId(), vencimiento);
        ordenes.guardar(orden);

        return new IniciarCompraResponse(ordenId, codigoPagoDe(ordenId), vencimiento.toString());
    }

    @Override
    public OrdenCompra seleccionarAsientos(String ordenId, List<String> asientosId) {

        OrdenCompra orden = ordenes.buscarPorId(ordenId);
        if (orden == null) {
            throw new ExcepcionNegocio("La orden %s no existe".formatted(ordenId));
        }

        // Una ventana vencida no se puede pagar aunque el barrido todavia no haya corrido.
        if (politicaExpiracion.expiro(orden, reloj.ahora())) {
            throw new OrdenExpiradaException(
                    "La orden %s vencio; hay que seleccionar asientos de nuevo".formatted(ordenId));
        }

        // La seleccion que muestra el checkout tiene que ser la que quedo bloqueada en la orden.
        Set<String> bloqueados = new HashSet<>();
        for (Asiento asiento : orden.getAsientos()) {
            bloqueados.add(asiento.getId());
        }
        if (!bloqueados.equals(new HashSet<>(asientosId))) {
            throw new ExcepcionNegocio("Los asientos enviados no corresponden a la orden %s".formatted(ordenId));
        }

        return orden;
    }

    /**
     * Codigo corto y estable que el front muestra para referenciar el pago; sale del id
     * de la orden para no guardar un dato extra.
     */
    private String codigoPagoDe(String ordenId) {
        return "PAY-" + ordenId.substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
