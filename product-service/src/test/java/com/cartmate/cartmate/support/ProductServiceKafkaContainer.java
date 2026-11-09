package com.cartmate.cartmate.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Kafka를 켠 테스트가 쓰는 실제 브로커. 한 번 띄워서 여러 테스트 클래스가 같이 쓴다(order-service와 같은 방식).
 *
 * <p>{@code @ImportTestcontainers(ProductServiceKafkaContainer.class)}로 가져오면 spring.kafka.bootstrap-servers가 이 브로커로 잡힌다.
 */
public final class ProductServiceKafkaContainer {

    @ServiceConnection
    static final KafkaContainer KAFKA = new SingletonKafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));

    static {
        KAFKA.start();
    }

    private ProductServiceKafkaContainer() {
    }

    static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    // 테스트 클래스마다 멈추지 않고, JVM이 끝날 때 Testcontainers가 정리한다
    private static final class SingletonKafkaContainer extends KafkaContainer {

        private SingletonKafkaContainer(DockerImageName image) {
            super(image);
        }

        @Override
        public void stop() {
        }
    }
}
