package com.cartmate.cartmate.global.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 릴레이 결과를 행마다 새 트랜잭션으로 기록한다.
 * 싱크 호출 자체는 트랜잭션 밖에서 한다: in-process 싱크의 consumer가 자기 트랜잭션을 rollback-only로 만들어도
 * 여기 기록은 살아야 하고, 한 행의 실패가 같은 배치의 다른 행에 번지면 안 된다.
 */
@Component
@RequiredArgsConstructor
class OutboxRelayWorker {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(Long id, LocalDateTime publishedAt) {
        outboxEventRepository.findById(id).ifPresent(event -> event.markPublished(publishedAt));
    }

    /**
     * @return 최대 시도에 이르러 FAILED가 됐으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(Long id, String error, int maxAttempts) {
        return outboxEventRepository.findById(id)
                .map(event -> event.recordFailure(error, maxAttempts))
                .orElse(false);
    }
}
