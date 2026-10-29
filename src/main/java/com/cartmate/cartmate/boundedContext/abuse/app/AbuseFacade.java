package com.cartmate.cartmate.boundedContext.abuse.app;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AbuseFacade {

    private final AbuseSignalReceiveUseCase abuseSignalReceiveUseCase;
    private final AbuseJudgeUseCase abuseJudgeUseCase;

    @Transactional
    public void receive(AbuseSignalCommand command) {
        abuseSignalReceiveUseCase.receive(command, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int judgePending(Long memberId) {
        return abuseJudgeUseCase.judgePending(memberId, LocalDateTime::now);
    }
}
