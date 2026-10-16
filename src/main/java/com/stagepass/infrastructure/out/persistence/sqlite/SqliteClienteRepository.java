package com.stagepass.infrastructure.out.persistence.sqlite;

import com.stagepass.domain.model.Cliente;
import com.stagepass.ports.out.ClienteRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Adaptador secundario del catalogo legacy de clientes. El nucleo lo consulta para
 * componer la orden y avisar al comprador; aqui solo se lee, jamas se escribe.
 */
public class SqliteClienteRepository implements ClienteRepository {

    private final JdbcTemplate jdbc;

    public SqliteClienteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Cliente buscarPorId(String clienteId) {
        List<Cliente> encontrados = jdbc.query(
                "SELECT id, nombre_completo, correo_electronico, telefono_movil, nacionalidad "
                        + "FROM clientes WHERE id = ?",
                (rs, fila) -> new Cliente(
                        rs.getString("id"),
                        rs.getString("nombre_completo"),
                        rs.getString("correo_electronico"),
                        rs.getString("telefono_movil"),
                        rs.getString("nacionalidad")),
                clienteId);
        // "No existe" se comunica devolviendo null: el caso de uso es quien decide el error.
        return encontrados.isEmpty() ? null : encontrados.get(0);
    }
}
