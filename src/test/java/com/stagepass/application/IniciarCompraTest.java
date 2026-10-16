package com.stagepass.application;

import com.stagepass.application.dobles.AsientoRepositoryEnMemoria;
import com.stagepass.application.dobles.ClienteRepositoryEnMemoria;
import com.stagepass.application.dobles.OrdenRepositoryEnMemoria;
import com.stagepass.application.dobles.RelojFijo;
import com.stagepass.domain.exception.AsientoNoDisponibleException;
import com.stagepass.domain.exception.ExcepcionNegocio;
import com.stagepass.domain.exception.LimiteBoletasExcedidoException;
import com.stagepass.domain.exception.OrdenExpiradaException;
import com.stagepass.domain.model.Asiento;
import com.stagepass.domain.model.Cliente;
import com.stagepass.domain.model.EstadoAsiento;
import com.stagepass.domain.model.EstadoOrden;
import com.stagepass.domain.model.OrdenCompra;
import com.stagepass.domain.service.CalculadoraTarifaria;
import com.stagepass.domain.service.PoliticaExpiracion;
import com.stagepass.domain.service.ValidadorLimites;
import com.stagepass.ports.in.SolicitudCompra;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IniciarCompraTest {

    private static final Instant AHORA = Instant.parse("2026-10-15T12:00:00Z");

    private AsientoRepositoryEnMemoria asientos;
    private OrdenRepositoryEnMemoria ordenes;
    private ClienteRepositoryEnMemoria clientes;
    private RelojFijo reloj;
    private IniciarCompra casoDeUso;

    @BeforeEach
    void preparar() {
        asientos = new AsientoRepositoryEnMemoria();
        ordenes = new OrdenRepositoryEnMemoria();
        clientes = new ClienteRepositoryEnMemoria();
        reloj = new RelojFijo(AHORA);

        clientes.agregar(new Cliente("cliente-1", "Ana", "ana@correo.test", "3001234567", "CO"));
        asientos.agregar(new Asiento("a-1", "recinto-1", "VIP-A12", new BigDecimal("100000")));
        asientos.agregar(new Asiento("a-2", "recinto-1", "VIP-A13", new BigDecimal("100000")));
        asientos.agregar(new Asiento("a-3", "recinto-1", "General-01", new BigDecimal("80000")));

        casoDeUso = new IniciarCompra(asientos, ordenes, clientes,
                new CalculadoraTarifaria(), new ValidadorLimites(), new PoliticaExpiracion(), reloj);
    }

    @Test
    void creaLaOrdenConLaTarifaDelTramoYSusCienPorCiento() {
        var respuesta = casoDeUso.iniciarCompra(
                new SolicitudCompra("evento-1", "cliente-1", List.of("a-1", "a-2")));

        OrdenCompra orden = ordenes.buscarPorId(respuesta.ordenId());
        assertThat(orden.getEstado()).isEqualTo(EstadoOrden.PENDIENTE_PAGO);
        assertThat(orden.getTarifaServicio()).isEqualByComparingTo("20000.00");
        assertThat(orden.getDescuentoAplicado()).isEqualByComparingTo("0.00");
        assertThat(orden.getMontoTotal()).isEqualByComparingTo("220000.00");
        assertThat(orden.getAsientos()).hasSize(2);

        assertThat(respuesta.codigoPago()).startsWith("PAY-");
        assertThat(respuesta.expiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(10)).toString());
    }

    @Test
    void bloqueaLosAsientosHastaElVencimientoDeLaVentana() {
        casoDeUso.iniciarCompra(new SolicitudCompra("evento-1", "cliente-1", List.of("a-1", "a-2")));

        Asiento bloqueado = asientos.buscarPorId("a-1");
        assertThat(bloqueado.getEstado()).isEqualTo(EstadoAsiento.BLOQUEADO);
        assertThat(bloqueado.getBloqueadoHasta()).isEqualTo(AHORA.plus(Duration.ofMinutes(10)));
    }

    @Test
    void rechazaLaCompraSiUnAsientoNoEstaDisponible() {
        asientos.buscarPorId("a-2").vender();

        assertThatThrownBy(() -> casoDeUso.iniciarCompra(
                new SolicitudCompra("evento-1", "cliente-1", List.of("a-1", "a-2"))))
                .isInstanceOf(AsientoNoDisponibleException.class);

        // Nada queda a medias: el asiento libre sigue libre y no hay orden guardada.
        assertThat(asientos.buscarPorId("a-1").estaDisponible()).isTrue();
        assertThat(ordenes.buscarActivasPorClienteYEvento("cliente-1", "evento-1")).isEmpty();
    }

    @Test
    void rechazaCuandoElClienteSuperaElTopeDe6Boletas() {
        // Una orden previa con 5 sillas: sumadas a las 2 nuevas, se pasaria del tope.
        ordenes.guardar(new OrdenCompra("previa-1", "evento-1", clientes.buscarPorId("cliente-1"),
                List.of(
                        new Asiento("s-1", "recinto-1", "S-1", new BigDecimal("50000")),
                        new Asiento("s-2", "recinto-1", "S-2", new BigDecimal("50000")),
                        new Asiento("s-3", "recinto-1", "S-3", new BigDecimal("50000")),
                        new Asiento("s-4", "recinto-1", "S-4", new BigDecimal("50000")),
                        new Asiento("s-5", "recinto-1", "S-5", new BigDecimal("50000"))),
                new BigDecimal("20000.00"), new BigDecimal("25000.00"), new BigDecimal("245000.00"),
                AHORA.plus(Duration.ofMinutes(10))));

        assertThatThrownBy(() -> casoDeUso.iniciarCompra(
                new SolicitudCompra("evento-1", "cliente-1", List.of("a-1", "a-2"))))
                .isInstanceOf(LimiteBoletasExcedidoException.class);
    }

    @Test
    void exigeQueElClienteExista() {
        assertThatThrownBy(() -> casoDeUso.iniciarCompra(
                new SolicitudCompra("evento-1", "cliente-fantasma", List.of("a-1"))))
                .isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void seleccionarAsientosDevuelveLaOrdenConSuSeleccion() {
        var respuesta = casoDeUso.iniciarCompra(
                new SolicitudCompra("evento-1", "cliente-1", List.of("a-1", "a-2")));

        OrdenCompra orden = casoDeUso.seleccionarAsientos(respuesta.ordenId(), List.of("a-1", "a-2"));

        assertThat(orden.getId()).isEqualTo(respuesta.ordenId());
        assertThat(orden.getAsientos()).extracting(Asiento::getId).containsExactly("a-1", "a-2");
    }

    @Test
    void seleccionarAsientosRechazaUnaSeleccionAjenaALaOrden() {
        var respuesta = casoDeUso.iniciarCompra(
                new SolicitudCompra("evento-1", "cliente-1", List.of("a-1", "a-2")));

        assertThatThrownBy(() -> casoDeUso.seleccionarAsientos(respuesta.ordenId(), List.of("a-3")))
                .isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    void seleccionarAsientosRechazaUnaOrdenVencida() {
        var respuesta = casoDeUso.iniciarCompra(
                new SolicitudCompra("evento-1", "cliente-1", List.of("a-1", "a-2")));
        reloj.avanzar(Duration.ofMinutes(11));

        assertThatThrownBy(() -> casoDeUso.seleccionarAsientos(respuesta.ordenId(), List.of("a-1", "a-2")))
                .isInstanceOf(OrdenExpiradaException.class);
    }
}
