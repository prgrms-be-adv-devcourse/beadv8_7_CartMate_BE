package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.inbox.ProcessedEvents;
import com.cartmate.cartmate.global.outbox.InProcessOutboxRelaySink;
import com.cartmate.cartmate.global.outbox.OutboxRelaySink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    @Bean
    public KafkaOutboxRelaySink kafkaOutboxRelaySink(KafkaTemplate<String, String> kafkaTemplate,
                                                     ObjectMapper objectMapper,
                                                     @Value("${cartmate.kafka.send-timeout:10s}") Duration sendTimeout) {
        return new KafkaOutboxRelaySink(kafkaTemplate, objectMapper, sendTimeout);
    }

    @Bean
    @Primary
    public OutboxRelaySink compositeOutboxRelaySink(InProcessOutboxRelaySink inProcessOutboxRelaySink,
                                                    KafkaOutboxRelaySink kafkaOutboxRelaySink) {
        return new CompositeOutboxRelaySink(inProcessOutboxRelaySink, kafkaOutboxRelaySink);
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer toDeadLetter = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> {
            log.error("Kafka 메시지 처리 실패 → DLT: topic={}, partition={}, offset={}, key={}",
                    record.topic(), record.partition(), record.offset(), record.key(), exception);
            toDeadLetter.accept(record, exception);
        }, new FixedBackOff(1_000L, 2L));
        handler.addNotRetryableExceptions(JacksonException.class);
        return handler;
    }

    @Bean
    @SuppressWarnings("unchecked")
    public KafkaInboundEventBridge kafkaInboundEventBridge(ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory,
                                                           ObjectProvider<InboundEventBinding> bindings,
                                                           EventEnvelopeReader envelopeReader,
                                                           ApplicationEventPublisher applicationEventPublisher,
                                                           PlatformTransactionManager transactionManager,
                                                           ProcessedEvents processedEvents) {
        return new KafkaInboundEventBridge(
                (ConcurrentKafkaListenerContainerFactory<String, String>) kafkaListenerContainerFactory,
                bindings.orderedStream().toList(),
                envelopeReader,
                applicationEventPublisher,
                new TransactionTemplate(transactionManager),
                processedEvents);
    }
}
