package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.inbox.InboxWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.CommonErrorHandler;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;


@Slf4j
public class KafkaInboundEventBridge implements SmartLifecycle {

    private final ConcurrentKafkaListenerContainerFactory<String, String> containerFactory;
    private final InboundEventTypes inboundEventTypes;
    private final EventEnvelopeReader envelopeReader;
    private final InboxWriter inboxWriter;

    private final List<InboundEventBinding> bindings;
    private final Function<Duration, CommonErrorHandler> errorHandlerFactory;
    private final Duration defaultRetryTimeout;
    private volatile List<ConcurrentMessageListenerContainer<String, String>> containers = List.of();

    public KafkaInboundEventBridge(ConcurrentKafkaListenerContainerFactory<String, String> containerFactory,
                                   InboundEventTypes inboundEventTypes,
                                   EventEnvelopeReader envelopeReader,
                                   InboxWriter inboxWriter,
                                   List<InboundEventBinding> bindings,
                                   Function<Duration, CommonErrorHandler> errorHandlerFactory,
                                   Duration defaultRetryTimeout) {
        this.containerFactory = containerFactory;
        this.inboundEventTypes = inboundEventTypes;
        this.envelopeReader = envelopeReader;
        this.inboxWriter = inboxWriter;
        this.bindings = List.copyOf(bindings);
        this.errorHandlerFactory = errorHandlerFactory;
        this.defaultRetryTimeout = defaultRetryTimeout;
    }

    void onMessage(ConsumerRecord<String, String> record) {
        Class<?> type = inboundEventTypes.typeOf(record.topic())
                .orElseThrow(() -> new IllegalStateException("받기로 선언하지 않은 토픽: " + record.topic()));
        EventEnvelope envelope = envelopeReader.envelope(record.value());
        envelopeReader.payload(envelope, type);
        if (!inboxWriter.save(record.topic(), envelope, record.value())) {
            log.info("이미 받은 이벤트 건너뜀: topic={}, eventId={}", record.topic(), envelope.eventId());
        }
    }

    @Override
    public synchronized void start() {
        if (bindings.isEmpty() || !containers.isEmpty()) {
            return;
        }
        List<ConcurrentMessageListenerContainer<String, String>> created = new ArrayList<>();
        for (InboundEventBinding binding : bindings) {
            Duration timeout = binding.retryTimeout() != null ? binding.retryTimeout() : defaultRetryTimeout;
            ConcurrentMessageListenerContainer<String, String> container = containerFactory.createContainer(binding.topic());
            container.setCommonErrorHandler(errorHandlerFactory.apply(timeout));
            container.setupMessageListener((MessageListener<String, String>) this::onMessage);
            container.start();
            created.add(container);
        }
        containers = List.copyOf(created);
        log.info("Kafka 수신 시작: topics={}", inboundEventTypes.topics());
    }

    @Override
    public synchronized void stop() {
        containers.forEach(ConcurrentMessageListenerContainer::stop);
        containers = List.of();
    }

    @Override
    public boolean isRunning() {
        return !containers.isEmpty() && containers.stream().allMatch(ConcurrentMessageListenerContainer::isRunning);
    }
}
