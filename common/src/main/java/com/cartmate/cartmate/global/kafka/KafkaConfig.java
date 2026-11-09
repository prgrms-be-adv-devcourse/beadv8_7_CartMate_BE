package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.inbox.InboxEventRepository;
import com.cartmate.cartmate.global.inbox.InboxProcessor;
import com.cartmate.cartmate.global.inbox.InboxProcessorProperties;
import com.cartmate.cartmate.global.inbox.InboxWorker;
import com.cartmate.cartmate.global.inbox.InboxWriter;
import com.cartmate.cartmate.global.outbox.DomainEventTypeRegistry;
import com.cartmate.cartmate.global.outbox.InProcessOutboxRelaySink;
import com.cartmate.cartmate.global.outbox.OutboxRelaySink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
@EnableConfigurationProperties(InboxProcessorProperties.class)
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
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
            @Value("${cartmate.kafka.retry-timeout:1m}") Duration retryTimeout) {
        return errorHandler(kafkaTemplate, retryTimeout);
    }

    static DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate, Duration retryTimeout) {
        DeadLetterPublishingRecoverer toDeadLetter = new DeadLetterPublishingRecoverer(kafkaTemplate);
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxInterval(30_000L);
        backOff.setMaxElapsedTime(retryTimeout.toMillis());
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> {
            log.error("Kafka 메시지 처리 실패 → DLT: topic={}, partition={}, offset={}, key={}",
                    record.topic(), record.partition(), record.offset(), record.key(), exception);
            toDeadLetter.accept(record, exception);
        }, backOff);
        handler.addNotRetryableExceptions(JacksonException.class);
        return handler;
    }

    @Bean
    public InboundEventTypes inboundEventTypes(ObjectProvider<InboundEventBinding> bindings) {
        return InboundEventTypes.of(bindings.orderedStream().toList());
    }

    @Bean
    @SuppressWarnings("unchecked")
    public KafkaInboundEventBridge kafkaInboundEventBridge(ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory,
                                                           InboundEventTypes inboundEventTypes,
                                                           EventEnvelopeReader envelopeReader,
                                                           InboxWriter inboxWriter,
                                                           ObjectProvider<InboundEventBinding> bindings,
                                                           KafkaTemplate<String, String> kafkaTemplate,
                                                           @Value("${cartmate.kafka.retry-timeout:1m}") Duration retryTimeout) {
        return new KafkaInboundEventBridge(
                (ConcurrentKafkaListenerContainerFactory<String, String>) kafkaListenerContainerFactory,
                inboundEventTypes,
                envelopeReader,
                inboxWriter, bindings.orderedStream().toList(),
                timeout -> errorHandler(kafkaTemplate, timeout), retryTimeout);
    }

    @Bean
    public InboxWorker inboxWorker(InboxEventRepository inboxEventRepository,
                                   EventEnvelopeReader envelopeReader,
                                   InboundEventTypes inboundEventTypes,
                                   ApplicationEventPublisher applicationEventPublisher,
                                   InboxProcessorProperties inboxProcessorProperties,
                                   PlatformTransactionManager transactionManager) {
        return new InboxWorker(inboxEventRepository, envelopeReader, inboundEventTypes,
                applicationEventPublisher, inboxProcessorProperties, transactionManager);
    }

    @Bean
    @ConditionalOnProperty(name = "cartmate.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    public InboxProcessor inboxProcessor(InboxEventRepository inboxEventRepository, InboxWorker inboxWorker,
                                         InboxProcessorProperties inboxProcessorProperties) {
        return new InboxProcessor(inboxEventRepository, inboxWorker, inboxProcessorProperties);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public KafkaRelayedEventConsumer kafkaRelayedEventConsumer(ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory,
                                                               ObjectProvider<RelaySubscription> subscriptions,
                                                               DomainEventTypeRegistry typeRegistry,
                                                               EventEnvelopeReader envelopeReader,
                                                               ApplicationEventPublisher applicationEventPublisher) {
        return new KafkaRelayedEventConsumer(
                (ConcurrentKafkaListenerContainerFactory<String, String>) kafkaListenerContainerFactory,
                subscriptions.orderedStream().toList(),
                typeRegistry,
                envelopeReader,
                applicationEventPublisher);
    }
}
