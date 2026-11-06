package com.cartmate.cartmate.support;

import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(properties = {
        "cartmate.scheduling.enabled=false",
        "custom.outbox.relay.initial-delay=PT1H",
        "cartmate.kafka.enabled=true"
})
@ActiveProfiles("test")
@ImportTestcontainers({CartMateTestContainers.class, CartMateKafkaContainer.class})
@ExtendWith(DatabaseCleaner.class)
@Import(ManualOutboxRelayConfig.class)
public @interface CartMateKafkaIntegrationTest {
}
