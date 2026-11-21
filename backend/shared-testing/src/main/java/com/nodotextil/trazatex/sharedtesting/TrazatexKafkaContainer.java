package com.nodotextil.trazatex.sharedtesting;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public class TrazatexKafkaContainer extends KafkaContainer {

    public static final String IMAGE = "apache/kafka:4.2.2";

    public TrazatexKafkaContainer() {
        super(DockerImageName.parse(IMAGE));
        withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
    }
}
