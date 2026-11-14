package com.cartmate.cartmate.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

public final class CartMateKafkaContainer {

    @ServiceConnection
    static final KafkaContainer KAFKA = new SingletonKafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));

    static {
        KAFKA.start();
    }

    private CartMateKafkaContainer() {
    }

    static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    private static final class SingletonKafkaContainer extends KafkaContainer {

        private SingletonKafkaContainer(DockerImageName image) {
            super(image);
        }

        @Override
        public void stop() {
        }
    }
}
