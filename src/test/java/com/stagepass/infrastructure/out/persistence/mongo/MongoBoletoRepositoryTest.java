package com.stagepass.infrastructure.out.persistence.mongo;

import com.stagepass.domain.model.Boleto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Integracion del adaptador de boletas contra el contenedor Mongo real. */
class MongoBoletoRepositoryTest {

    private static final Instant EMISION = Instant.parse("2026-10-16T08:05:00.250Z");

    private MongoTemplate mongo;
    private MongoBoletoRepository repo;

    @BeforeEach
    void prepararColeccion() {
        mongo = MongoDePrueba.plantilla();
        mongo.dropCollection("boletos");
        repo = new MongoBoletoRepository(mongo);
    }

    @Test
    void guardarYBuscarPorCodigoDevuelveElBoletoConTodoSiguales() {
        Boleto boleto = new Boleto("BLT-001", "ORD-001", "SP-1A2B3C4D",
                "9f86d081884c7d659a2feaa0c55ad015", EMISION);

        repo.guardar(boleto);
        Boleto encontrado = repo.buscarPorCodigo("SP-1A2B3C4D");

        assertThat(encontrado).isNotNull();
        assertThat(encontrado.getId()).isEqualTo("BLT-001");
        assertThat(encontrado.getOrdenId()).isEqualTo("ORD-001");
        assertThat(encontrado.getFirmaHash()).isEqualTo("9f86d081884c7d659a2feaa0c55ad015");
        assertThat(encontrado.getFechaEmision()).isEqualTo(EMISION);
    }

    @Test
    void buscarPorCodigoDevuelveNullCuandoElCodigoNoExiste() {
        assertThat(repo.buscarPorCodigo("SP-XXXXXXXX")).isNull();
    }

    @Test
    void elCodigoDeRedencionEsUnicoYRechazaUnSegundoBoletoIgual() {
        repo.guardar(new Boleto("BLT-101", "ORD-101", "SP-DUP1234", "firma-a", EMISION));

        // El indice unico del arranque es la segunda barrera frente a reentregas del webhook.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> repo.guardar(new Boleto("BLT-102", "ORD-102", "SP-DUP1234", "firma-b", EMISION)))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }
}
