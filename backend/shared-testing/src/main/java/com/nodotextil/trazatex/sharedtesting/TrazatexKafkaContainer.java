package com.nodotextil.trazatex.sharedtesting;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * El Kafka de las pruebas: la misma imagen y el mismo comportamiento que el del {@code docker-compose.yml}. En particular
 * <b>no crea tópicos solos</b> ({@code auto.create.topics.enable=false}, ver {@code docs/conventions.md}): un tópico que nadie
 * declaró no existe y publicar en él falla. Con el valor por omisión de la imagen el broker los crea al primer uso, y una
 * prueba que olvidara declarar un tópico (o que creara uno ajeno sin querer) pasaría sin probar lo que dice.
 *
 * <p>Toda prueba con Kafka real usa esta clase en lugar de {@code new KafkaContainer(...)}. La versión de la imagen está en un
 * solo lugar y una prueba ({@code TrazatexKafkaContainerTest}) comprueba que coincide con la del compose.
 */
public class TrazatexKafkaContainer extends KafkaContainer {

    /** Misma versión exacta que {@code docker-compose.yml}. */
    public static final String IMAGE = "apache/kafka:4.2.2";

    public TrazatexKafkaContainer() {
        super(DockerImageName.parse(IMAGE));
        withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    }
}
