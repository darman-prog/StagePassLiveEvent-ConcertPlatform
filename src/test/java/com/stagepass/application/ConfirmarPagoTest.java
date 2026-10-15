package com.stagepass.application;

import com.stagepass.application.dobles.AsientoRepositoryEnMemoria;
import com.stagepass.application.dobles.BoletoRepositoryEnMemoria;
import com.stagepass.application.dobles.FacturaEmitterFalso;
import com.stagepass.application.dobles.NotificadorFalso;
import com.stagepass.application.dobles.OrdenRepositoryEnMemoria;
import com.stagepass.application.dobles.PaymentGatewayFalso;
import com.stagepass.application.dobles.RelojFijo;
import com.stagepass.application.dobles.TicketGeneratorFalso;
import com.stagepass.domain.exception.ExcepcionNegocio;
import com.stagepass.domain.exception.OrdenExpiradaException;
import com.stagepass.domain.exception.PagoNoVerificadoException;
import com.stagepass.domain.model.Asiento;
import com.stagepass.domain.model.Cliente;
import com.stagepass.domain.model.EstadoAsiento;
import com.stagepass.domain.model.EstadoOrden;
import com.stagepass.domain.model.OrdenCompra;
import com.stagepass.domain.service.GeneradorFirma;
import com.stagepass.domain.service.PoliticaExpiracion;
import com.stagepass.ports.in.ConfirmacionPago;
import com.stagepass.ports.out.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfirmarPagoTest {

    private static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");

    private AsientoRepositoryEnMemoria asientos;
    private OrdenRepositoryEnMemoria ordenes;
    private BoletoRepositoryEnMemoria boletos;
    private NotificadorFalso notificador;
    private FacturaEmitterFalso facturas;
    private RelojFijo reloj;
    private ConfirmarPago casoDeUso;

    @BeforeEach
    void preparar() {
        asientos = new AsientoRepositoryEnMemoria();
        ordenes = new OrdenRepositoryEnMemoria();
        boletos = new BoletoRepositoryEnMemoria();
        notificador = new NotificadorFalso();
        facturas = new FacturaEmitterFalso();
        reloj = new RelojFijo(AHORA);

        // Dos pasarelas de mentira: una aprueba y la otra rechaza, para cubrir ambos caminos.
        Map<String, PaymentGateway> pasarelas = Map.of(
                "wompi", new PaymentGatewayFalso("wompi", PaymentGateway.EstadoPago.APROBADO),
                "pse", new PaymentGatewayFalso("pse", PaymentGateway.EstadoPago.RECHAZADO));

        casoDeUso = new ConfirmarPago(ordenes, asientos, boletos, pasarelas,
                new TicketGeneratorFalso(), notificador, facturas,
                new GeneradorFirma(), new PoliticaExpiracion(), reloj,
                "clave-test", "https://stagepass.test/validar");

        Cliente cliente = new Cliente("cliente-1", "Ana", "ana@correo.test", "3001234567", "CO");
        Asiento a1 = new Asiento("a-1", "recinto-1", "VIP-A12", new BigDecimal("100000"));
        Asiento a2 = new Asiento("a-2", "recinto-1", "VIP-A13", new BigDecimal("100000"));
        asientos.agregar(a1);
        asientos.agregar(a2);

        ordenes.guardar(new OrdenCompra("orden-1", "evento-1", cliente, List.of(a1, a2),
                new BigDecimal("20000.00"), new BigDecimal("0.00"), new BigDecimal("220000.00"),
                AHORA.plus(Duration.ofMinutes(10))));
        asientos.bloquear(List.of("a-1", "a-2"), AHORA.plus(Duration.ofMinutes(10)));
    }

    @Test
    void elPagoAprobadoEmiteElBoletoYVendeLasSillas() {
        var boleto = casoDeUso.confirmarPago(new ConfirmacionPago("orden-1", "wompi", "tx-1"));

        assertThat(boletos.buscarPorCodigo(boleto.getCodigoRedencion())).isSameAs(boleto);
        // La firma es un SHA-256 en hexadecimal: 64 caracteres.
        assertThat(boleto.getFirmaHash()).hasSize(64);
        assertThat(boleto.getCodigoRedencion()).startsWith("SP-");
        assertThat(boleto.getFechaEmision()).isEqualTo(AHORA);

        assertThat(ordenes.buscarPorId("orden-1").getEstado()).isEqualTo(EstadoOrden.APROBADA);
        assertThat(asientos.buscarPorId("a-1").getEstado()).isEqualTo(EstadoAsiento.VENDIDO);
        assertThat(asientos.buscarPorId("a-2").getEstado()).isEqualTo(EstadoAsiento.VENDIDO);

        // El comprador recibe el aviso con su codigo y la factura quedo emitida.
        assertThat(notificador.enviadas()).hasSize(1);
        assertThat(notificador.enviadas().get(0).destinatario()).isEqualTo("ana@correo.test");
        assertThat(notificador.enviadas().get(0).mensaje()).contains(boleto.getCodigoRedencion());
        assertThat(facturas.ultima()).isNotNull();
        assertThat(facturas.ultima().ordenId()).isEqualTo("orden-1");
        assertThat(facturas.ultima().monto()).isEqualByComparingTo("220000.00");
    }

    @Test
    void elPagoRechazadoNoEmiteBoletoNiVendeLasSillas() {
        assertThatThrownBy(() -> casoDeUso.confirmarPago(new ConfirmacionPago("orden-1", "pse", "tx-2")))
                .isInstanceOf(PagoNoVerificadoException.class);

        assertThat(ordenes.buscarPorId("orden-1").getEstado()).isEqualTo(EstadoOrden.PENDIENTE_PAGO);
        assertThat(asientos.buscarPorId("a-1").getEstado()).isEqualTo(EstadoAsiento.BLOQUEADO);
        assertThat(boletos.cantidad()).isZero();
        assertThat(notificador.enviadas()).isEmpty();
    }

    @Test
    void noSePuedePagarUnaOrdenVencida() {
        reloj.avanzar(Duration.ofMinutes(11));

        assertThatThrownBy(() -> casoDeUso.confirmarPago(new ConfirmacionPago("orden-1", "wompi", "tx-1")))
                .isInstanceOf(OrdenExpiradaException.class);

        // La orden vencida queda marcada como tal para que el barrido la deje fuera.
        assertThat(ordenes.buscarPorId("orden-1").getEstado()).isEqualTo(EstadoOrden.EXPIRADA);
        assertThat(boletos.cantidad()).isZero();
    }

    @Test
    void noSePuedeConfirmarDosVecesLaMismaOrden() {
        casoDeUso.confirmarPago(new ConfirmacionPago("orden-1", "wompi", "tx-1"));

        assertThatThrownBy(() -> casoDeUso.confirmarPago(new ConfirmacionPago("orden-1", "wompi", "tx-1")))
                .isInstanceOf(ExcepcionNegocio.class);

        assertThat(boletos.cantidad()).isEqualTo(1);
    }

    @Test
    void rechazaUnaPasarelaQueNoEstaRegistrada() {
        assertThatThrownBy(() -> casoDeUso.confirmarPago(new ConfirmacionPago("orden-1", "nequi", "tx-3")))
                .isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void rechazaUnaOrdenQueNoExiste() {
        assertThatThrownBy(() -> casoDeUso.confirmarPago(new ConfirmacionPago("orden-9", "wompi", "tx-9")))
                .isInstanceOf(ExcepcionNegocio.class);
    }
}
