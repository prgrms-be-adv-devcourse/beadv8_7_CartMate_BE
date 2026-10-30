package com.cartmate.cartmate.boundedContext.abuse.app;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AbuseFacade {

    private final AbuseSignalUseCase abuseSignalUseCase;
    private final AbuseSignalReceiveUseCase abuseSignalReceiveUseCase;

    @Transactional
    public void receive(AbuseSignalCommand command) {
        abuseSignalReceiveUseCase.receive(command, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSharedGroupOrderCanceled(SharedGroupOrderCanceledEvent event, Long memberId) {
        abuseSignalUseCase.handleSharedGroupOrderCanceled(event, memberId, LocalDateTime.now());
    }
}
