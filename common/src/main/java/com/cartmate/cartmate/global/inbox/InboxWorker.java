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

    // 리스너는 이 트랜잭션 안에서 불리므로, 리스너가 잡은 분산락은 커밋 전에 풀린다.
    // 그래서 이벤트 경로는 분산락 대신 행 락 + @Version으로 막는다.
    // 분산락으로 바꾸려면 여기서 트랜잭션을 열기 전에 이벤트로 락 키를 구해 잡고, 커밋 뒤에 풀어야 한다.
    // 이때 처리와 PROCESSED 표시는 그대로 한 트랜잭션이지만, 아래 비용이 따른다.
    // - 락 키를 API와 맞춰야 한다: API는 배송 단위, 취소 판정은 주문의 배송 전부라 키 설계와 배송 생성 × 취소 순서를 다시 봐야 한다.
    // - 이벤트는 한 건씩 차례로 처리되므로 한 건이 락을 기다리면 뒤 이벤트가 모두 밀리고, Redis 장애 때 이벤트 처리도 멈춘다.
    // - 모든 서비스가 이 코드를 같이 쓰므로 전 서비스 회귀 확인이 필요하다.
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
