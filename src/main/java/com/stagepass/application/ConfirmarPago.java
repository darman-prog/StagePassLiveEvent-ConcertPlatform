package com.stagepass.application;

import com.stagepass.domain.exception.ExcepcionNegocio;
import com.stagepass.domain.exception.OrdenExpiradaException;
import com.stagepass.domain.exception.PagoNoVerificadoException;
import com.stagepass.domain.model.Asiento;
import com.stagepass.domain.model.Boleto;
import com.stagepass.domain.model.EstadoOrden;
import com.stagepass.domain.model.OrdenCompra;
import com.stagepass.domain.service.GeneradorFirma;
import com.stagepass.domain.service.PoliticaExpiracion;
import com.stagepass.ports.in.ConfirmacionPago;
import com.stagepass.ports.in.NotificacionTransaccion;
import com.stagepass.ports.in.PagosUseCase;
import com.stagepass.ports.out.AsientoRepository;
import com.stagepass.ports.out.BoletoRepository;
import com.stagepass.ports.out.Comprobante;
import com.stagepass.ports.out.Factura;
import com.stagepass.ports.out.FacturaEmitter;
import com.stagepass.ports.out.Notificacion;
import com.stagepass.ports.out.Notificador;
import com.stagepass.ports.out.OrdenRepository;
import com.stagepass.ports.out.PaymentGateway;
import com.stagepass.ports.out.Reloj;
import com.stagepass.ports.out.RepresentacionBoleto;
import com.stagepass.ports.out.TicketGenerator;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso de pago: verifica la transaccion contra la pasarela elegida y, si esta
 * aprobada, aprueba la orden, vende las sillas y emite el boleto firmado.
 */
public class ConfirmarPago implements PagosUseCase {

    private final OrdenRepository ordenes;
    private final AsientoRepository asientos;
    private final BoletoRepository boletos;
    private final Map<String, PaymentGateway> pasarelas;
    private final TicketGenerator tickets;
    private final Notificador notificador;
    private final FacturaEmitter facturas;
    private final GeneradorFirma generadorFirma;
    private final PoliticaExpiracion politicaExpiracion;
    private final Reloj reloj;
    private final String claveSecretaEventos;
    private final String urlBaseValidacion;

    /**
     * Las pasarelas entran como mapa nombre -> implementacion: PayPal, Wompi y PSE son
     * adaptadores intercambiables y el nombre de la pasarela elige cual se consulta.
     */
    public ConfirmarPago(OrdenRepository ordenes, AsientoRepository asientos, BoletoRepository boletos,
                         Map<String, PaymentGateway> pasarelas, TicketGenerator tickets, Notificador notificador,
                         FacturaEmitter facturas, GeneradorFirma generadorFirma,
                         PoliticaExpiracion politicaExpiracion, Reloj reloj,
                         String claveSecretaEventos, String urlBaseValidacion) {
        this.ordenes = ordenes;
        this.asientos = asientos;
        this.boletos = boletos;
        this.pasarelas = pasarelas;
        this.tickets = tickets;
        this.notificador = notificador;
        this.facturas = facturas;
        this.generadorFirma = generadorFirma;
        this.politicaExpiracion = politicaExpiracion;
        this.reloj = reloj;
        this.claveSecretaEventos = claveSecretaEventos;
        this.urlBaseValidacion = urlBaseValidacion;
    }

    @Override
    public Boleto confirmarPago(ConfirmacionPago confirmacion) {

        Instant ahora = reloj.ahora();

        OrdenCompra orden = ordenes.buscarPorId(confirmacion.ordenId());
        if (orden == null) {
            throw new ExcepcionNegocio("La orden %s no existe".formatted(confirmacion.ordenId()));
        }

        // Una orden vencida se marca como tal de una vez: la ventana de 10 minutos es la regla.
        if (politicaExpiracion.expiro(orden, ahora)) {
            orden.expirar();
            ordenes.guardar(orden);
            throw new OrdenExpiradaException(
                    "La orden %s vencio antes de confirmar el pago".formatted(orden.getId()));
        }

        // Solo se paga una orden pendiente: evita emitir un segundo boleto si el webhook reintenta.
        if (orden.getEstado() != EstadoOrden.PENDIENTE_PAGO) {
            throw new ExcepcionNegocio(
                    "La orden %s no esta pendiente de pago (estado %s)".formatted(orden.getId(), orden.getEstado()));
        }

        PaymentGateway pasarela = pasarelas.get(confirmacion.pasarela());
        if (pasarela == null) {
            throw new ExcepcionNegocio("La pasarela %s no esta registrada".formatted(confirmacion.pasarela()));
        }

        // La pasarela decide: se consulta la transaccion que el comprador ya inicio alla.
        var referencia = new PaymentGateway.ReferenciaPago(confirmacion.pasarela(), confirmacion.identificadorTransaccion());
        if (pasarela.consultarEstado(referencia) != PaymentGateway.EstadoPago.APROBADO) {
            throw new PagoNoVerificadoException(
                    "La transaccion %s no llego aprobada por %s".formatted(
                            confirmacion.identificadorTransaccion(), confirmacion.pasarela()));
        }

        orden.aprobar();
        ordenes.guardar(orden);

        List<String> idsAsientos = orden.getAsientos().stream().map(Asiento::getId).toList();
        asientos.marcarVendidos(idsAsientos);

        // La firma cubre los datos inmutables de la orden mas la clave del evento: si alguien
        // edita el monto o la expiracion en la base, la validacion en puerta deja de pasar.
        String firma = generadorFirma.generar(orden, claveSecretaEventos);
        String boletoId = UUID.randomUUID().toString();
        String codigo = generadorFirma.generarCodigoRedencion(boletoId);

        Boleto boleto = new Boleto(boletoId, orden.getId(), codigo, firma, ahora);
        boletos.guardar(boleto);

        // Representacion del boleto (QR mock), factura y aviso al comprador: los tres puertos
        // de salida que dispara un pago aprobado.
        RepresentacionBoleto representacion = tickets.generar(codigo, firma, urlBaseValidacion + "/" + codigo);
        Comprobante comprobante = facturas.emitir(
                new Factura(orden.getId(), orden.getCliente().getId(), orden.getMontoTotal()));
        notificador.enviar(new Notificacion(orden.getCliente().getCorreoElectronico(),
                "Tu boleto %s esta activo (factura %s): %s".formatted(codigo, comprobante.numero(), representacion.url())));

        return boleto;
    }

    @Override
    public void notificarCambioTransaccion(String pasarela, NotificacionTransaccion notificacion) {
        // El webhook de la pasarela llega sin el id de la orden, asi que el nucleo no puede
        // mover datos con el aviso; el empuje en vivo a la vista lo resuelve el adaptador web.
    }
}
