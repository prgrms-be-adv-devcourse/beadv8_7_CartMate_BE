package com.cartmate.cartmate.boundedContext.abuse.app;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
class AbuseJudgeRetryUseCase {

    static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    private final AbuseSignalRepository abuseSignalRepository;

    List<Long> findPendingMemberIds(LocalDateTime now, int limit) {
        return abuseSignalRepository.findMemberIdsByStatusReceivedBefore(
                AbuseSignalStatus.RECEIVED, now.minus(RETRY_DELAY), Limit.of(limit));
    }

    void recordFailure(Long memberId, String error, LocalDateTime now) {
        int recorded = abuseSignalRepository.recordFailureOfReceived(
                memberId, AbuseSignal.truncateError(error), AbuseSignal.MAX_JUDGE_ATTEMPTS, now);
        if (recorded == 0) {
            return;
        }
        long failed = abuseSignalRepository.countByMemberIdAndStatusAndUpdatedAt(memberId, AbuseSignalStatus.FAILED, now);
        if (failed > 0) {
            log.error("어뷰징 신호 판정을 {}회 실패해 FAILED로 전환: memberId={}, failedSignals={}, lastError={}",
                    AbuseSignal.MAX_JUDGE_ATTEMPTS, memberId, failed, error);
        }
    }
}
