package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.inbox.InboxWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;


@Slf4j
public class KafkaInboundEventBridge implements SmartLifecycle {

    private final ConcurrentKafkaListenerContainerFactory<String, String> containerFactory;
    private final InboundEventTypes inboundEventTypes;
    private final EventEnvelopeReader envelopeReader;
    private final InboxWriter inboxWriter;

    private volatile ConcurrentMessageListenerContainer<String, String> container;

    public KafkaInboundEventBridge(ConcurrentKafkaListenerContainerFactory<String, String> containerFactory,
                                   InboundEventTypes inboundEventTypes,
                                   EventEnvelopeReader envelopeReader,
                                   InboxWriter inboxWriter) {
        this.containerFactory = containerFactory;
        this.inboundEventTypes = inboundEventTypes;
        this.envelopeReader = envelopeReader;
        this.inboxWriter = inboxWriter;
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
    public void start() {
        if (inboundEventTypes.topics().isEmpty() || container != null) {
            return;
        }
        ConcurrentMessageListenerContainer<String, String> created =
                containerFactory.createContainer(inboundEventTypes.topics().toArray(String[]::new));
        created.setupMessageListener((MessageListener<String, String>) this::onMessage);
        created.start();
        container = created;
        log.info("Kafka 수신 시작: topics={}", inboundEventTypes.topics());
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
