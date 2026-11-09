package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.inbox.ProcessedEvents;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

@Slf4j
public class InboundEvents {

    private final EventEnvelopeReader envelopeReader;
    private final ProcessedEvents processedEvents;
    private final TransactionTemplate transactionTemplate;

    public InboundEvents(EventEnvelopeReader envelopeReader,
                         ProcessedEvents processedEvents,
                         TransactionTemplate transactionTemplate) {
        this.envelopeReader = envelopeReader;
        this.processedEvents = processedEvents;
        this.transactionTemplate = transactionTemplate;
    }

    public <T> void process(ConsumerRecord<String, String> record, Class<T> eventType, Consumer<T> handler) {
        EventEnvelope envelope = envelopeReader.envelope(record.value());
        T event = envelopeReader.payload(envelope, eventType);
        transactionTemplate.executeWithoutResult(status -> {
            if (!processedEvents.markFirst(consumerName(record.topic()), envelope.eventId())) {
                log.info("이미 처리한 이벤트 건너뜀: topic={}, eventId={}", record.topic(), envelope.eventId());
                return;
            }
            handler.accept(event);
        });
    }

    private static String consumerName(String topic) {
        return "kafka:" + topic;
    }
}
