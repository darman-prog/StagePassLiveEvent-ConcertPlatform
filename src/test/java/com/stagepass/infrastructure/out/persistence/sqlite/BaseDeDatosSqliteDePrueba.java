package com.stagepass.infrastructure.out.persistence.sqlite;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Prepara una base SQLite de prueba EN MEMORIA con los mismos schema.sql y data.sql que
 * usa la aplicacion en produccion. Una sola conexion mantiene vivo la base mientras dure
 * el test y evita los candados de archivo que Windows no libera al borrar temporales.
 */
public final class BaseDeDatosSqliteDePrueba {

    private BaseDeDatosSqliteDePrueba() {
    }

    public static JdbcTemplate crear() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        cargarScripts(jdbc);
        return jdbc;
    }

    /** Vuelve a ejecutar los dos scripts: sirve para verificar que arranques repetidos no duplican seeds. */
    public static void cargarScripts(JdbcTemplate jdbc) {
        try (Connection conexion = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(conexion, recurso("schema.sql"));
            ScriptUtils.executeSqlScript(conexion, recurso("data.sql"));
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo preparar la base de prueba", e);
        }
    }

    private static EncodedResource recurso(String nombre) {
        return new EncodedResource(new ClassPathResource(nombre), StandardCharsets.UTF_8);
    }
}
