package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.inbox.ProcessedEvents;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class KafkaInboundEventBridge implements SmartLifecycle {

    private final ConcurrentKafkaListenerContainerFactory<String, String> containerFactory;
    private final Map<String, Class<?>> eventTypeByTopic;
    private final EventEnvelopeReader envelopeReader;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ProcessedEvents processedEvents;

    private volatile ConcurrentMessageListenerContainer<String, String> container;

    public KafkaInboundEventBridge(ConcurrentKafkaListenerContainerFactory<String, String> containerFactory,
                                   List<InboundEventBinding> bindings,
                                   EventEnvelopeReader envelopeReader,
                                   ApplicationEventPublisher applicationEventPublisher,
                                   TransactionTemplate transactionTemplate,
                                   ProcessedEvents processedEvents) {
        this.containerFactory = containerFactory;
        this.eventTypeByTopic = bindings.stream()
                .collect(Collectors.toMap(InboundEventBinding::topic, InboundEventBinding::eventType));
        this.envelopeReader = envelopeReader;
        this.applicationEventPublisher = applicationEventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.processedEvents = processedEvents;
    }

    void onMessage(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = envelopeReader.envelope(record.value());
        Object event = envelopeReader.payload(envelope, eventTypeByTopic.get(record.topic()));
        transactionTemplate.executeWithoutResult(status -> {
            if (!processedEvents.markFirst(consumerName(record.topic()), envelope.eventId())) {
                log.info("이미 처리한 이벤트 건너뜀: topic={}, eventId={}", record.topic(), envelope.eventId());
                return;
            }
            applicationEventPublisher.publishEvent(event);
        });
    }

    static String consumerName(String topic) {
        return "kafka:" + topic;
    }

    @Override
    public void start() {
        if (eventTypeByTopic.isEmpty() || container != null) {
            return;
        }
        ConcurrentMessageListenerContainer<String, String> created =
                containerFactory.createContainer(eventTypeByTopic.keySet().toArray(String[]::new));
        created.setupMessageListener((MessageListener<String, String>) this::onMessage);
        created.start();
        container = created;
        log.info("Kafka 수신 시작: topics={}", eventTypeByTopic.keySet());
    }

    @Override
    public void stop() {
        ConcurrentMessageListenerContainer<String, String> current = container;
        if (current != null) {
            current.stop();
            container = null;
        }
    }

    @Override
    public boolean isRunning() {
        return container != null && container.isRunning();
    }
}
