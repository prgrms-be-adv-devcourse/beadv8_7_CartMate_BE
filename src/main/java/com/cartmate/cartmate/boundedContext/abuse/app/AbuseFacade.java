package com.cartmate.cartmate.boundedContext.abuse.app;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AbuseFacade {

    private final AbuseSignalReceiveUseCase abuseSignalReceiveUseCase;
    private final AbuseJudgeUseCase abuseJudgeUseCase;
    private final AbuseJudgeRetryUseCase abuseJudgeRetryUseCase;

    @Transactional
    public void receive(AbuseSignalCommand command) {
        abuseSignalReceiveUseCase.receive(command, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int judgePending(Long memberId) {
        return abuseJudgeUseCase.judgePending(memberId, LocalDateTime::now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordJudgeFailure(Long memberId, String error) {
        abuseJudgeRetryUseCase.recordFailure(memberId, error, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<Long> findPendingMemberIds(int limit) {
        return abuseJudgeRetryUseCase.findPendingMemberIds(LocalDateTime.now(), limit);
    }
}
