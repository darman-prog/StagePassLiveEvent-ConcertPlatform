package com.stagepass.infrastructure.out.persistence.sqlite;

import com.stagepass.domain.model.Asiento;
import com.stagepass.domain.model.EstadoAsiento;
import com.stagepass.ports.out.AsientoRepository;
import com.stagepass.ports.out.Reloj;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador secundario del catalogo legacy: lee y mueve los asientos de SQLite con
 * JdbcTemplate. No calcula nada de negocio; las transiciones de estado las hace la
 * propia entidad del dominio y la expiracion se delega a PoliticaExpiracion via bloques.
 */
public class SqliteAsientoRepository implements AsientoRepository {

    private static final String COLUMNAS = "id, recinto_id, codigo_ubicacion, precio_base, estado, bloqueado_hasta";

    private final JdbcTemplate jdbc;
    private final Reloj reloj;

    public SqliteAsientoRepository(JdbcTemplate jdbc, Reloj reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Override
    public List<Asiento> listarPorRecinto(String recintoId) {
        Instant ahora = reloj.ahora();
        List<Asiento> asientos = jdbc.query(
                "SELECT " + COLUMNAS + " FROM asientos WHERE recinto_id = ? ORDER BY id",
                this::mapear, recintoId);
        for (Asiento asiento : asientos) {
            devolverSiBloqueoVencio(asiento, ahora);
        }
        return asientos;
    }

    @Override
    public Asiento buscarPorId(String asientoId) {
        List<Asiento> encontrados = jdbc.query(
                "SELECT " + COLUMNAS + " FROM asientos WHERE id = ?",
                this::mapear, asientoId);
        if (encontrados.isEmpty()) {
            return null;
        }
        Asiento asiento = encontrados.get(0);
        devolverSiBloqueoVencio(asiento, reloj.ahora());
        return asiento;
    }

    @Override
    public void bloquear(List<String> asientosId, Instant hasta) {
        if (asientosId.isEmpty()) {
            return;
        }
        // Solo pasan un asiento DISPONIBLE a BLOQUEADO: asi el UPDATE jamas pisa el
        // bloqueo de otro comprador que gano la carrera entre el chequeo y esta escritura.
        String sql = "UPDATE asientos SET estado = 'BLOQUEADO', bloqueado_hasta = ? "
                + "WHERE id IN (" + marcasPara(asientosId.size()) + ") AND estado = 'DISPONIBLE'";
        List<Object> parametros = new ArrayList<>();
        parametros.add(hasta.toString());
        parametros.addAll(asientosId);
        jdbc.update(sql, parametros.toArray());
    }

    @Override
    public void marcarVendidos(List<String> asientosId) {
        if (asientosId.isEmpty()) {
            return;
        }
        String sql = "UPDATE asientos SET estado = 'VENDIDO', bloqueado_hasta = NULL "
                + "WHERE id IN (" + marcasPara(asientosId.size()) + ")";
        jdbc.update(sql, asientosId.toArray());
    }

    @Override
    public void liberar(List<String> asientosId) {
        if (asientosId.isEmpty()) {
            return;
        }
        // Sin AND estado <> 'VENDIDO': si el barrido llegara tarde despues de un pago
        // aprobado, no revenderia un boleto ya culminado el bloqueo vendido.
        String sql = "UPDATE asientos SET estado = 'DISPONIBLE', bloqueado_hasta = NULL "
                + "WHERE id IN (" + marcasPara(asientosId.size()) + ") AND estado <> 'VENDIDO'";
        jdbc.update(sql, asientosId.toArray());
    }

    /**
     * Limpieza resiliente: un bloqueo cuya ventana ya paso vuelve al pool al primer
     * lector. Deja la fila y la entidad coherentes sin esperar el barrido del job.
     */
    private void devolverSiBloqueoVencio(Asiento asiento, Instant ahora) {
        if (asiento.bloqueoVencido(ahora)) {
            jdbc.update(
                    "UPDATE asientos SET estado = 'DISPONIBLE', bloqueado_hasta = NULL "
                            + "WHERE id = ? AND estado = 'BLOQUEADO'",
                    asiento.getId());
            asiento.liberar();
        }
    }

    /**
     * Reconstruye la entidad con sus transiciones de dominio (bloquear/vender) en vez
     * de settear campos a mano, para que la fila y el objeto queden coherentes.
     */
    private Asiento mapear(ResultSet rs, int fila) throws SQLException {
        Asiento asiento = new Asiento(
                rs.getString("id"),
                rs.getString("recinto_id"),
                rs.getString("codigo_ubicacion"),
                new BigDecimal(rs.getString("precio_base")));

        EstadoAsiento estado = EstadoAsiento.valueOf(rs.getString("estado"));
        String bloqueadoHasta = rs.getString("bloqueado_hasta");
        switch (estado) {
            case BLOQUEADO -> asiento.bloquear(Instant.parse(bloqueadoHasta));
            case VENDIDO -> asiento.vender();
            case DISPONIBLE -> { /* el constructor nace disponible */ }
        }
        return asiento;
    }

    private String marcasPara(int cantidad) {
        return String.join(",", java.util.Collections.nCopies(cantidad, "?"));
    }
}
