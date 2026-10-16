package com.stagepass.infrastructure.in.web;

import com.stagepass.domain.exception.AsientoNoDisponibleException;
import com.stagepass.domain.exception.ExcepcionNegocio;
import com.stagepass.domain.exception.LimiteBoletasExcedidoException;
import com.stagepass.domain.exception.OrdenExpiradaException;
import com.stagepass.domain.exception.PagoNoVerificadoException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduccion de las excepciones del nucleo a HTTP con criterio (CONTRATO §5). El cuerpo
 * es ProblemDetail RFC 7807; el campo detail lleva el mensaje de negocio del nucleo.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorDeErrores.class);
    private static final String TIPO_BASE = "https://stagepass.example/errores/";

    @ExceptionHandler(OrdenExpiradaException.class)
    public ProblemDetail ordenVencida(OrdenExpiradaException e) {
        return problema(HttpStatus.GONE, "La ventana de pago vencio", e);
    }

    /** Conflicto con el estado actual: silla tomada, limite del cliente o paquete que no cuadra. */
    @ExceptionHandler({AsientoNoDisponibleException.class, LimiteBoletasExcedidoException.class})
    public ProblemDetail conflictoDeNegocio(ExcepcionNegocio e) {
        return problema(HttpStatus.CONFLICT, "Regla de negocio no cumplida", e);
    }

    @ExceptionHandler(PagoNoVerificadoException.class)
    public ProblemDetail pagoNoVerificado(PagoNoVerificadoException e) {
        return problema(HttpStatus.PAYMENT_REQUIRED, "El pago no llego aprobado", e);
    }

    @ExceptionHandler(ExcepcionNegocio.class)
    public ProblemDetail excepcionGenerica(ExcepcionNegocio e) {
        // El nucleo usa la base para los "no existe" y para validaciones de estado:
        // se distingue por el mensaje para no inventar excepciones nuevas por contrato.
        if (e.getMessage() != null && e.getMessage().contains("no existe")) {
            return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", e);
        }
        return problema(HttpStatus.CONFLICT, "Estado incompatible con la operacion", e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail peticionInvalida(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((primero, segundo) -> primero + "; " + segundo)
                .orElse("La peticion no cumple el formato");
        return problema(HttpStatus.BAD_REQUEST, "Peticion invalida", detalle);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail cuerpoIlegible(HttpMessageNotReadableException e) {
        // Un JSON roto o un cuerpo vacio se resuelven en el borde con 400 y no caen en 500.
        return problema(HttpStatus.BAD_REQUEST, "Peticion invalida", "El cuerpo no es un JSON valido o falta");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail errorInesperado(Exception e) {
        LOG.error("Error no controlado", e);
        // Sin stack trace al cliente: solo el detalle tecnico minimo.
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error inesperado", e.getClass().getSimpleName());
    }

    private ProblemDetail problema(HttpStatus status, String titulo, ExcepcionNegocio e) {
        return problema(status, titulo, e.getMessage());
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setType(URI.create(TIPO_BASE + status.value()));
        problema.setTitle(titulo);
        return problema;
    }
}
