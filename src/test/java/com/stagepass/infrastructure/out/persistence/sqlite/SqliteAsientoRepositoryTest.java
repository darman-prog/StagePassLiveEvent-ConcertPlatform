package com.stagepass.infrastructure.out.persistence.sqlite;

import com.stagepass.domain.model.Asiento;
import com.stagepass.domain.model.EstadoAsiento;
import com.stagepass.ports.out.Reloj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integracion del adaptador SQLite contra una base en memoria con los scripts de
 * produccion: aqui se prueba el contrato del puerto, no detalles de SQL.
 */
class SqliteAsientoRepositoryTest {

    private static final Instant AHORA = Instant.parse("2026-10-16T08:00:00Z");
    private static final Instant VENCIMIENTO = AHORA.plusSeconds(600);

    private final Reloj relojFijo = () -> AHORA;

    private JdbcTemplate jdbc;
    private SqliteAsientoRepository repo;

    @BeforeEach
    void prepararBase() {
        jdbc = BaseDeDatosSqliteDePrueba.crear();
        repo = new SqliteAsientoRepository(jdbc, relojFijo);
    }

    @Test
    void listarPorRecintoDevuelveLosDiezAsientosSembrados() {
        List<Asiento> asientos = repo.listarPorRecinto("REC-01");

        assertThat(asientos).hasSize(10);
        assertThat(asientos).allMatch(Asiento::estaDisponible);
        // ORDER BY id: las general (GEN-*) ordenan antes que las VIP.
        assertThat(asientos.getFirst().getCodigoUbicacion()).isEqualTo("General-01");
    }

    @Test
    void buscarPorIdMantieneElPrecioExactoDelAsiento() {
        Asiento vip = repo.buscarPorId("VIP-A1");
        Asiento general = repo.buscarPorId("GEN-01");

        assertThat(vip.getPrecioBase()).isEqualByComparingTo("150000.00");
        assertThat(general.getPrecioBase()).isEqualByComparingTo("80000.00");
    }

    @Test
    void bloquearGuardaEstadoYVencimientoEnLaFila() {
        repo.bloquear(List.of("VIP-A1"), VENCIMIENTO);

        Asiento bloqueado = repo.buscarPorId("VIP-A1");
        assertThat(bloqueado.getEstado()).isEqualTo(EstadoAsiento.BLOQUEADO);
        assertThat(bloqueado.getBloqueadoHasta()).isEqualTo(VENCIMIENTO);
    }

    @Test
    void bloquearJamasPisaUnBloqueoPrevioDeOtroComprador() {
        repo.bloquear(List.of("VIP-A1"), VENCIMIENTO);

        // Un segundo comprador intenta extender el vencimiento de la misma silla: no puede.
        repo.bloquear(List.of("VIP-A1"), AHORA.plusSeconds(3600));

        Asiento asiento = repo.buscarPorId("VIP-A1");
        assertThat(asiento.getBloqueadoHasta()).isEqualTo(VENCIMIENTO);
    }

    @Test
    void marcarVendidosLimpiaElVencimientoDelBloqueo() {
        repo.bloquear(List.of("GEN-01"), VENCIMIENTO);

        repo.marcarVendidos(List.of("GEN-01"));

        Asiento vendido = repo.buscarPorId("GEN-01");
        assertThat(vendido.getEstado()).isEqualTo(EstadoAsiento.VENDIDO);
        assertThat(vendido.getBloqueadoHasta()).isNull();
    }

    @Test
    void liberarNoRevendeUnAsientoYaVendido() {
        repo.bloquear(List.of("GEN-02"), VENCIMIENTO);
        repo.marcarVendidos(List.of("GEN-02"));

        // El barrido de expiracion podria llegar tarde y pedir liberar: no revende.
        repo.liberar(List.of("GEN-02"));

        assertThat(repo.buscarPorId("GEN-02").getEstado()).isEqualTo(EstadoAsiento.VENDIDO);
    }

    @Test
    void liberarDevuelveAlPoolUnBloqueoVigente() {
        repo.bloquear(List.of("GEN-03"), VENCIMIENTO);

        repo.liberar(List.of("GEN-03"));

        Asiento libre = repo.buscarPorId("GEN-03");
        assertThat(libre.getEstado()).isEqualTo(EstadoAsiento.DISPONIBLE);
        assertThat(libre.getBloqueadoHasta()).isNull();
    }

    @Test
    void laLecturaAutoLiberaUnBloqueoCuyaVentanaYaPaso() {
        // Bloqueo residual a mano: simula una escritura cuya orden murio despues de
        // bloquear, dejando el bloqueo huerfano en la base relacional sin orden que lo libere.
        jdbc.update("UPDATE asientos SET estado = 'BLOQUEADO', bloqueado_hasta = ? WHERE id = ?",
                AHORA.minusSeconds(60).toString(), "VIP-A2");

        Asiento asiento = repo.buscarPorId("VIP-A2");

        assertThat(asiento.getEstado()).isEqualTo(EstadoAsiento.DISPONIBLE);
        assertThat(asiento.getBloqueadoHasta()).isNull();

        // Y la fila quedo coherente: no vuelve a aparecer bloqueada en la proxima lectura.
        Integer bloqueados = jdbc.queryForObject(
                "SELECT COUNT(*) FROM asientos WHERE id = 'VIP-A2' AND estado = 'BLOQUEADO'", Integer.class);
        assertThat(bloqueados).isZero();
    }

    @Test
    void repetirLosScriptsDeArranqueNoDuplicaNingunAsiento() {
        BaseDeDatosSqliteDePrueba.cargarScripts(jdbc);

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM asientos WHERE recinto_id = 'REC-01'", Integer.class);
        assertThat(total).isEqualTo(10);
    }
}
