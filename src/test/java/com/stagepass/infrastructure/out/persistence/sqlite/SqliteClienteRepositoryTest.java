package com.stagepass.infrastructure.out.persistence.sqlite;

import com.stagepass.domain.model.Cliente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Integracion del catalogo legacy de clientes contra una base en memoria con seeds reales. */
class SqliteClienteRepositoryTest {

    private SqliteClienteRepository repo;

    @BeforeEach
    void prepararBase() {
        JdbcTemplate jdbc = BaseDeDatosSqliteDePrueba.crear();
        repo = new SqliteClienteRepository(jdbc);
    }

    @Test
    void buscarPorIdDevuelveElClienteCompletoSembrado() {
        Cliente cliente = repo.buscarPorId("CL-002");

        assertThat(cliente).isNotNull();
        assertThat(cliente.getId()).isEqualTo("CL-002");
        assertThat(cliente.getNombreCompleto()).isEqualTo("Andres Villalba");
        assertThat(cliente.getCorreoElectronico()).isEqualTo("andres.villalba@example.com");
        assertThat(cliente.getTelefonoMovil()).isEqualTo("573007654321");
        assertThat(cliente.getNacionalidad()).isEqualTo("CO");
    }

    @Test
    void buscarPorIdDevuelveNullCuandoElClienteNoExiste() {
        assertThat(repo.buscarPorId("CL-999")).isNull();
    }
}
