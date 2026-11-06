package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.shared.abuse.dto.AbuseRestrictionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AbuseFacade {

    private final AbuseSignalReceiveUseCase abuseSignalReceiveUseCase;
    private final AbuseJudgeUseCase abuseJudgeUseCase;
    private final AbuseJudgeRetryUseCase abuseJudgeRetryUseCase;
    private final AbuseRestrictionQueryUseCase abuseRestrictionQueryUseCase;
    private final Clock clock;

    @Transactional
    public void receive(AbuseSignalCommand command) {
        abuseSignalReceiveUseCase.receive(command, LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int judgePending(Long memberId) {
        return abuseJudgeUseCase.judgePending(memberId, () -> LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordJudgeFailure(Long memberId, String error) {
        abuseJudgeRetryUseCase.recordFailure(memberId, error, LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public List<Long> findPendingMemberIds(int limit) {
        return abuseJudgeRetryUseCase.findPendingMemberIds(LocalDateTime.now(clock), limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public AbuseRestrictionStatus getRestriction(Long memberId) {
        return abuseRestrictionQueryUseCase.getRestriction(memberId, LocalDateTime.now(clock));
    }
}
