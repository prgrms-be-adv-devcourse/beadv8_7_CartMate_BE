package com.cartmate.cartmate.boundedContext.abuse.app;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseSignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class AbuseSignalReceiveUseCase {

    private final AbuseSignalRepository abuseSignalRepository;

    void receive(AbuseSignalCommand command, LocalDateTime receivedAt) {
        abuseSignalRepository.insertIfAbsent(
                command.memberId(),
                command.type().name(),
                command.sourceKey(),
                command.occurredAt(),
                receivedAt,
                AbuseSignalStatus.RECEIVED.name()
        );
    }
}
