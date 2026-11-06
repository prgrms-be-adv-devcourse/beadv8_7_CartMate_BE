package com.cartmate.cartmate.global.inbox;

import com.cartmate.cartmate.global.kafka.EventEnvelopeReader;
import com.cartmate.cartmate.global.kafka.InboundEventTypes;
import com.cartmate.cartmate.global.outbox.NonRetryableRelayException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;

import java.time.LocalDateTime;

public class InboxWorker {

    private final InboxEventRepository inboxEventRepository;
    private final EventEnvelopeReader envelopeReader;
    private final InboundEventTypes inboundEventTypes;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final InboxProcessorProperties properties;
    private final TransactionTemplate requiresNew;

    public InboxWorker(InboxEventRepository inboxEventRepository,
                       EventEnvelopeReader envelopeReader,
                       InboundEventTypes inboundEventTypes,
                       ApplicationEventPublisher applicationEventPublisher,
                       InboxProcessorProperties properties,
                       PlatformTransactionManager transactionManager) {
        this.inboxEventRepository = inboxEventRepository;
        this.envelopeReader = envelopeReader;
        this.inboundEventTypes = inboundEventTypes;
        this.applicationEventPublisher = applicationEventPublisher;
        this.properties = properties;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void process(Long id) {
        requiresNew.executeWithoutResult(status -> inboxEventRepository.findByIdForUpdate(id)
                .filter(row -> row.getStatus() == InboxEventStatus.NEW)
                .ifPresent(row -> {
                    applicationEventPublisher.publishEvent(toEvent(row));
                    row.markProcessed(LocalDateTime.now());
                }));
    }

    public boolean recordFailure(Long id, String error) {
        Boolean failed = requiresNew.execute(status -> inboxEventRepository.findById(id)
                .map(row -> row.recordFailure(error, properties, LocalDateTime.now()))
                .orElse(false));
        return Boolean.TRUE.equals(failed);
    }

    public boolean markFailed(Long id, String error) {
        Boolean failed = requiresNew.execute(status -> inboxEventRepository.findById(id)
                .map(row -> row.markFailed(error))
                .orElse(false));
        return Boolean.TRUE.equals(failed);
    }

    private Object toEvent(InboxEvent row) {
        Class<?> type = inboundEventTypes.typeOf(row.getTopic())
                .orElseThrow(() -> new NonRetryableRelayException(
                        "받기로 선언하지 않은 토픽: topic=" + row.getTopic() + ", eventId=" + row.getEventId(), null));
        try {
            return envelopeReader.payload(envelopeReader.envelope(row.getEnvelope()), type);
        } catch (JacksonException e) {
            throw new NonRetryableRelayException("이벤트를 되돌릴 수 없다: eventId=" + row.getEventId(), e);
        }
    }
}
