package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.global.outbox.DomainEventTypeRegistry;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class KafkaRelayedEventConsumer implements SmartLifecycle {

    private final ConcurrentKafkaListenerContainerFactory<String, String> containerFactory;
    private final List<RelaySubscription> subscriptions;
    private final DomainEventTypeRegistry typeRegistry;
    private final EventEnvelopeReader envelopeReader;
    private final ApplicationEventPublisher applicationEventPublisher;

    private final List<ConcurrentMessageListenerContainer<String, String>> containers = new ArrayList<>();

    public KafkaRelayedEventConsumer(ConcurrentKafkaListenerContainerFactory<String, String> containerFactory,
                                     List<RelaySubscription> subscriptions,
                                     DomainEventTypeRegistry typeRegistry,
                                     EventEnvelopeReader envelopeReader,
                                     ApplicationEventPublisher applicationEventPublisher) {
        this.containerFactory = containerFactory;
        this.subscriptions = subscriptions;
        this.typeRegistry = typeRegistry;
        this.envelopeReader = envelopeReader;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    void onMessage(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = envelopeReader.envelope(record.value());
        DomainEvent event = envelopeReader.payload(envelope, typeRegistry.resolve(envelope.eventType()));
        applicationEventPublisher.publishEvent(new OutboxMessageRelayed(envelope, event, true));
    }

    List<String> topicsOf(RelaySubscription subscription) {
        return typeRegistry.types().values().stream()
                .filter(type -> subscription.modules().contains(OutboxTopics.moduleOf(type)))
                .map(OutboxTopics::of)
                .sorted()
                .toList();
    }

    @Override
    public synchronized void start() {
        if (!containers.isEmpty()) {
            return;
        }
        for (RelaySubscription subscription : subscriptions) {
            List<String> topics = topicsOf(subscription);
            if (topics.isEmpty()) {
                continue;
            }
            ConcurrentMessageListenerContainer<String, String> container =
                    containerFactory.createContainer(topics.toArray(String[]::new));
            container.getContainerProperties().setGroupId(subscription.groupId());
            container.setupMessageListener((MessageListener<String, String>) this::onMessage);
            container.start();
            containers.add(container);
            log.info("Kafka 재전달 수신 시작: group={}, topics={}", subscription.groupId(), topics);
        }
    }

    @Override
    public synchronized void stop() {
        containers.forEach(ConcurrentMessageListenerContainer::stop);
        containers.clear();
    }

    @Override
    public synchronized boolean isRunning() {
        return !containers.isEmpty() && containers.stream().allMatch(ConcurrentMessageListenerContainer::isRunning);
    }
}
