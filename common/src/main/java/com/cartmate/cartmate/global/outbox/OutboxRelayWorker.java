package com.cartmate.cartmate.global.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
class OutboxRelayWorker {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxRelayProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(Long id, LocalDateTime publishedAt) {
        outboxEventRepository.findById(id).ifPresent(event -> event.markPublished(publishedAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(Long id, String error, LocalDateTime now) {
        return outboxEventRepository.findById(id)
                .map(event -> event.recordFailure(error, properties, now))
                .orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(Long id, String error) {
        return outboxEventRepository.findById(id)
                .map(event -> event.markFailed(error))
                .orElse(false);
    }
}
