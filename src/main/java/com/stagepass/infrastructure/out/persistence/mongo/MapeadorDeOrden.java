package com.stagepass.infrastructure.out.persistence.mongo;

import com.stagepass.domain.model.Asiento;
import com.stagepass.domain.model.Cliente;
import com.stagepass.domain.model.EstadoAsiento;
import com.stagepass.domain.model.EstadoOrden;
import com.stagepass.domain.model.OrdenCompra;
import com.stagepass.infrastructure.out.persistence.InstantTexto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Traduce entre la entidad de dominio y el documento Mongo. Las transiciones de estado
 * se rehacen con los metodos de la entidad (aprobar/expirar, bloquear/vender) para que
 * lo releido quede exactamente igual a lo guardado.
 */
public final class MapeadorDeOrden {

    private MapeadorDeOrden() {
    }

    public static OrdenDocumento aDocumento(OrdenCompra orden) {
        List<OrdenDocumento.AsientoDeOrden> asientos = orden.getAsientos().stream()
                .map(MapeadorDeOrden::aDocumento)
                .toList();
        return new OrdenDocumento(
                orden.getId(),
                orden.getEventoId(),
                aDocumento(orden.getCliente()),
                asientos,
                orden.getTarifaServicio().toPlainString(),
                orden.getDescuentoAplicado().toPlainString(),
                orden.getMontoTotal().toPlainString(),
                InstantTexto.formatear(orden.getExpiraEn()),
                orden.getEstado().name());
    }

    public static OrdenCompra aEntidad(OrdenDocumento documento) {
        Cliente cliente = aEntidad(documento.cliente());
        List<Asiento> asientos = documento.asientos().stream()
                .map(MapeadorDeOrden::aEntidad)
                .toList();

        OrdenCompra orden = new OrdenCompra(
                documento.id(),
                documento.eventoId(),
                cliente,
                asientos,
                new BigDecimal(documento.tarifaServicio()),
                new BigDecimal(documento.descuentoAplicado()),
                new BigDecimal(documento.montoTotal()),
                InstantTexto.aInstant(documento.expiraEn()));

        EstadoOrden estado = EstadoOrden.valueOf(documento.estado());
        switch (estado) {
            case APROBADA -> orden.aprobar();
            case RECHAZADA -> orden.rechazar();
            case EXPIRADA -> orden.expirar();
            case PENDIENTE_PAGO -> { /* el constructor nace pendiente */ }
        }
        return orden;
    }

    private static OrdenDocumento.ClienteDeOrden aDocumento(Cliente cliente) {
        return new OrdenDocumento.ClienteDeOrden(
                cliente.getId(),
                cliente.getNombreCompleto(),
                cliente.getCorreoElectronico(),
                cliente.getTelefonoMovil(),
                cliente.getNacionalidad());
    }

    private static Cliente aEntidad(OrdenDocumento.ClienteDeOrden documento) {
        return new Cliente(
                documento.id(),
                documento.nombreCompleto(),
                documento.correoElectronico(),
                documento.telefonoMovil(),
                documento.nacionalidad());
    }

    private static OrdenDocumento.AsientoDeOrden aDocumento(Asiento asiento) {
        return new OrdenDocumento.AsientoDeOrden(
                asiento.getId(),
                asiento.getRecintoId(),
                asiento.getCodigoUbicacion(),
                asiento.getPrecioBase().toPlainString(),
                asiento.getEstado().name(),
                asiento.getBloqueadoHasta() == null ? null : InstantTexto.formatear(asiento.getBloqueadoHasta()));
    }

    private static Asiento aEntidad(OrdenDocumento.AsientoDeOrden documento) {
        Asiento asiento = new Asiento(
                documento.id(),
                documento.recintoId(),
                documento.codigoUbicacion(),
                new BigDecimal(documento.precioBase()));

        EstadoAsiento estado = EstadoAsiento.valueOf(documento.estado());
        switch (estado) {
            case BLOQUEADO -> asiento.bloquear(InstantTexto.aInstant(documento.bloqueadoHasta()));
            case VENDIDO -> asiento.vender();
            case DISPONIBLE -> { /* el constructor nace disponible */ }
        }
        return asiento;
    }
}
