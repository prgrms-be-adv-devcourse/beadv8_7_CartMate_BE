package com.cartmate.cartmate.boundedContext.abuse.app;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseSignalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AbuseSignalReceiveUseCaseTest {

    @Mock
    private AbuseSignalRepository abuseSignalRepository;

    @InjectMocks
    private AbuseSignalReceiveUseCase abuseSignalReceiveUseCase;

    @Test
    @DisplayName("신호를 RECEIVED 상태로 중복 무시 저장한다")
    void insertsReceivedSignalIfAbsent() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 28, 12, 0);
        LocalDateTime receivedAt = occurredAt.plusSeconds(1);

        abuseSignalReceiveUseCase.receive(
                new AbuseSignalCommand(1L, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED, "100", occurredAt), receivedAt);

        then(abuseSignalRepository).should().insertIfAbsent(
                1L, "SHARED_ORDER_PAYMENT_EXPIRED", "100", occurredAt, receivedAt, "RECEIVED");
    }
}
