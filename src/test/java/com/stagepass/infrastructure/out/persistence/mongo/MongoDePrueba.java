package com.stagepass.infrastructure.out.persistence.mongo;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Contenedor MongoDB compartido por los tests de integracion. Reutiliza la imagen
 * mongo:7 que ya vive en el cache local de Docker; el arranque es de unos segundos.
 */
public final class MongoDePrueba {

    private static final MongoDBContainer CONTENEDOR = new MongoDBContainer(DockerImageName.parse("mongo:7"));

    private static MongoTemplate plantilla;

    private MongoDePrueba() {
    }

    /** Arranca el contenedor a la primera referencia y arma la plantilla sobre la base stagepass. */
    public static synchronized MongoTemplate plantilla() {
        if (!CONTENEDOR.isRunning()) {
            CONTENEDOR.start();
        }
        if (plantilla == null) {
            SimpleMongoClientDatabaseFactory fabrica =
                    new SimpleMongoClientDatabaseFactory(CONTENEDOR.getReplicaSetUrl("stagepass"));
            plantilla = new MongoTemplate(fabrica);
        }
        return plantilla;
    }
}
