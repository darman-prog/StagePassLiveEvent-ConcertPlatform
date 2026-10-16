package com.stagepass.infrastructure.config;

import com.stagepass.application.ConfirmarPago;
import com.stagepass.application.ExpirarReservas;
import com.stagepass.application.IniciarCompra;
import com.stagepass.application.ValidarBoleto;
import com.stagepass.domain.service.CalculadoraTarifaria;
import com.stagepass.domain.service.GeneradorFirma;
import com.stagepass.domain.service.PoliticaExpiracion;
import com.stagepass.domain.service.ValidadorLimites;
import com.stagepass.infrastructure.in.job.ReservasJob;
import com.stagepass.infrastructure.out.invoicing.DianFacturaEmitter;
import com.stagepass.infrastructure.out.notification.SendgridNotificador;
import com.stagepass.infrastructure.out.notification.WhatsappNotificador;
import com.stagepass.infrastructure.out.payment.PayPalPaymentGateway;
import com.stagepass.infrastructure.out.payment.PsePaymentGateway;
import com.stagepass.infrastructure.out.payment.WompiPaymentGateway;
import com.stagepass.infrastructure.out.persistence.mongo.MongoBoletoRepository;
import com.stagepass.infrastructure.out.persistence.mongo.MongoOrdenRepository;
import com.stagepass.infrastructure.out.persistence.sqlite.SqliteAsientoRepository;
import com.stagepass.infrastructure.out.persistence.sqlite.SqliteClienteRepository;
import com.stagepass.infrastructure.out.ticket.QrTicketGenerator;
import com.stagepass.infrastructure.out.time.SistemaReloj;
import com.stagepass.ports.out.AsientoRepository;
import com.stagepass.ports.out.BoletoRepository;
import com.stagepass.ports.out.ClienteRepository;
import com.stagepass.ports.out.FacturaEmitter;
import com.stagepass.ports.out.Notificador;
import com.stagepass.ports.out.OrdenRepository;
import com.stagepass.ports.out.PaymentGateway;
import com.stagepass.ports.out.Reloj;
import com.stagepass.ports.out.TicketGenerator;
import com.stagepass.ports.in.MantenimientoUseCase;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Map;

/**
 * Todo el cableado de la aplicacion vive AQUI: los adaptadores se inyectan en los casos
 * de uso del nucleo, que siguen sin saber de Spring, SQLite, Mongo ni pasarelas.
 *
 * Los adaptadores de Mongo van @Lazy con inyeccion perezosa: el arranque de la app no
 * abre la base (los indices se crean al primer uso); una demo o un test sin Docker
 * suben igual y el puerto falla solo si se intenta usar de verdad.
 *
 * Las pasarelas se registran con nombres de bean "paypal", "wompi" y "pse": esos nombres
 * son exactamente los valores que el front envia en el campo pasarela del checkout.
 */
@Configuration
@EnableScheduling
public class ConfiguracionSpring {

    // ---- Puertos secundarios: persistencia y servicios externos --------------------

    @Bean
    public Reloj relojDelSistema() {
        return new SistemaReloj();
    }

    @Bean
    public AsientoRepository sqliteAsientoRepository(JdbcTemplate jdbc, Reloj reloj) {
        return new SqliteAsientoRepository(jdbc, reloj);
    }

    @Bean
    public ClienteRepository sqliteClienteRepository(JdbcTemplate jdbc) {
        return new SqliteClienteRepository(jdbc);
    }

    @Bean
    @Lazy
    public OrdenRepository mongoOrdenRepository(MongoTemplate mongo) {
        return new MongoOrdenRepository(mongo);
    }

    @Bean
    @Lazy
    public BoletoRepository mongoBoletoRepository(MongoTemplate mongo) {
        return new MongoBoletoRepository(mongo);
    }

    @Bean("paypal")
    public PayPalPaymentGateway paypal() {
        return new PayPalPaymentGateway();
    }

    @Bean("wompi")
    public WompiPaymentGateway wompi() {
        return new WompiPaymentGateway();
    }

    @Bean("pse")
    public PsePaymentGateway pse() {
        return new PsePaymentGateway();
    }

    @Bean
    public TicketGenerator qrTicketGenerator() {
        return new QrTicketGenerator();
    }

    @Bean
    public FacturaEmitter dianFacturaEmitter() {
        return new DianFacturaEmitter();
    }

    @Bean
    @Primary
    public SendgridNotificador sendgridNotificador() {
        return new SendgridNotificador();
    }

    @Bean
    public WhatsappNotificador whatsappNotificador() {
        return new WhatsappNotificador();
    }

    // ---- Servicios de dominio que el nucleo no anota --------------------------------

    @Bean
    public CalculadoraTarifaria calculadoraTarifaria() {
        return new CalculadoraTarifaria();
    }

    @Bean
    public ValidadorLimites validadorLimites() {
        return new ValidadorLimites();
    }

    @Bean
    public PoliticaExpiracion politicaExpiracion() {
        return new PoliticaExpiracion();
    }

    @Bean
    public GeneradorFirma generadorFirma() {
        return new GeneradorFirma();
    }

    // ---- Casos de uso (implementan los puertos primarios) --------------------------

    @Bean
    public IniciarCompra iniciarCompra(AsientoRepository asientos, @Lazy OrdenRepository ordenes,
                                       ClienteRepository clientes, CalculadoraTarifaria calculadora,
                                       ValidadorLimites validadorLimites, PoliticaExpiracion politicaExpiracion,
                                       Reloj reloj) {
        return new IniciarCompra(asientos, ordenes, clientes, calculadora, validadorLimites, politicaExpiracion, reloj);
    }

    /**
     * El mapa nombre -> pasarela llega armado por Spring con los nombres de bean de
     * arriba; la clave que envia el front (paypal/wompi/pse) elige el adaptador.
     */
    @Bean
    public ConfirmarPago confirmarPago(@Lazy OrdenRepository ordenes, AsientoRepository asientos,
                                       @Lazy BoletoRepository boletos, Map<String, PaymentGateway> pasarelas,
                                       TicketGenerator tickets, Notificador notificador, FacturaEmitter facturas,
                                       GeneradorFirma generadorFirma, PoliticaExpiracion politicaExpiracion,
                                       Reloj reloj, @Value("${stagepass.clave-secreta-eventos}") String claveSecreta,
                                       @Value("${stagepass.url-base-validacion}") String urlBaseValidacion) {
        return new ConfirmarPago(ordenes, asientos, boletos, pasarelas, tickets, notificador, facturas,
                generadorFirma, politicaExpiracion, reloj, claveSecreta, urlBaseValidacion);
    }

    @Bean
    public ValidarBoleto validarBoleto(@Lazy BoletoRepository boletos, @Lazy OrdenRepository ordenes,
                                       GeneradorFirma generadorFirma,
                                       @Value("${stagepass.clave-secreta-eventos}") String claveSecreta) {
        return new ValidarBoleto(boletos, ordenes, generadorFirma, claveSecreta);
    }

    @Bean
    public ExpirarReservas expirarReservas(@Lazy OrdenRepository ordenes, AsientoRepository asientos,
                                           PoliticaExpiracion politicaExpiracion, Reloj reloj) {
        return new ExpirarReservas(ordenes, asientos, politicaExpiracion, reloj);
    }

    @Bean
    public ReservasJob reservasJob(MantenimientoUseCase mantenimiento) {
        return new ReservasJob(mantenimiento);
    }
}
