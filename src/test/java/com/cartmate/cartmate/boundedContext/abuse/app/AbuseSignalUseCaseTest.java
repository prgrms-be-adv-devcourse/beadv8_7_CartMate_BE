package com.cartmate.cartmate.boundedContext.abuse.app;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRuleResult;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.abuse.out.MemoryAbuseSignalCounter;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AbuseSignalUseCaseTest {

    private static final Long PAID = 1L;
    private static final Long UNPAID_A = 2L;
    private static final Long UNPAID_B = 3L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 12, 0);

    @Mock
    private AbuseJudgmentUseCase abuseJudgmentUseCase;

    private MemoryAbuseSignalCounter counter;

    private AbuseSignalUseCase abuseSignalUseCase;

    @BeforeEach
    void setUp() {
        counter = new MemoryAbuseSignalCounter();
        abuseSignalUseCase = new AbuseSignalUseCase(counter, List.of(new SharedOrderPaymentExpiredRule()), abuseJudgmentUseCase);
    }

    @Test
    @DisplayName("기한 만료로 무산되면 미결제 참여자마다 신호를 남기고 판정한다")
    void deadlineExpiredSignalsEachUnpaidMember() {
        SharedGroupOrderCanceledEvent event = event(100L, "DEADLINE_EXPIRED", List.of(UNPAID_A, UNPAID_B));
        event.unpaidMemberIds().forEach(memberId -> abuseSignalUseCase.handleSharedGroupOrderCanceled(event, memberId, NOW));

        ArgumentCaptor<AbuseSignalCommand> signals = ArgumentCaptor.forClass(AbuseSignalCommand.class);
        then(abuseJudgmentUseCase).should(times(2)).judge(signals.capture(), any(), eq(NOW));
        assertThat(signals.getAllValues()).extracting(AbuseSignalCommand::memberId).containsExactly(UNPAID_A, UNPAID_B);
        assertThat(signals.getAllValues()).allSatisfy(signal -> {
            assertThat(signal.type()).isEqualTo(AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED);
            assertThat(signal.sourceKey()).isEqualTo("100");
        });
        assertThat(counter.count(PAID, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED, NOW.minusDays(30))).isZero();
    }

    @Test
    @DisplayName("생성자 취소·재고 부족 무산은 신호가 아니다")
    void otherReasonsAreIgnored() {
        abuseSignalUseCase.handleSharedGroupOrderCanceled(event(100L, "CREATOR_CANCELED", List.of(UNPAID_A)), UNPAID_A, NOW);
        abuseSignalUseCase.handleSharedGroupOrderCanceled(event(101L, "OUT_OF_STOCK", List.of(UNPAID_A)), UNPAID_A, NOW);

        then(abuseJudgmentUseCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("30일 안 세 번째 무산이면 규칙 결과는 3단계 120점이고, 30일이 지난 무산은 세지 않는다")
    void thirdExpiryWithin30Days() {
        abuseSignalUseCase.handleSharedGroupOrderCanceled(
                event(1L, "DEADLINE_EXPIRED", List.of(UNPAID_A), NOW.minusDays(31)), UNPAID_A, NOW);
        abuseSignalUseCase.handleSharedGroupOrderCanceled(
                event(2L, "DEADLINE_EXPIRED", List.of(UNPAID_A), NOW.minusDays(20)), UNPAID_A, NOW);
        abuseSignalUseCase.handleSharedGroupOrderCanceled(
                event(3L, "DEADLINE_EXPIRED", List.of(UNPAID_A), NOW.minusDays(10)), UNPAID_A, NOW);
        abuseSignalUseCase.handleSharedGroupOrderCanceled(
                event(4L, "DEADLINE_EXPIRED", List.of(UNPAID_A), NOW), UNPAID_A, NOW);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AbuseRuleResult>> results = ArgumentCaptor.forClass(List.class);
        then(abuseJudgmentUseCase).should(times(4)).judge(any(), results.capture(), eq(NOW));
        AbuseRuleResult last = results.getAllValues().getLast().getFirst();
        assertThat(last.signalCount()).isEqualTo(3);
        assertThat(last.level()).isEqualTo(AbuseLevel.RESTRICTED);
        assertThat(last.score()).isEqualTo(120);
    }

    @Test
    @DisplayName("이미 판정한 신호가 다시 오면 세지도 판정하지도 않는다")
    void duplicateEventIsIgnored() {
        given(abuseJudgmentUseCase.isJudged(any())).willReturn(true);

        abuseSignalUseCase.handleSharedGroupOrderCanceled(event(100L, "DEADLINE_EXPIRED", List.of(UNPAID_A)), UNPAID_A, NOW);

        then(abuseJudgmentUseCase).should(never()).judge(any(), any(), any());
        assertThat(counter.count(UNPAID_A, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED, NOW.minusDays(30))).isZero();
    }

    private SharedGroupOrderCanceledEvent event(Long orderId, String reason, List<Long> unpaidMemberIds) {
        return event(orderId, reason, unpaidMemberIds, NOW);
    }

    private SharedGroupOrderCanceledEvent event(Long orderId, String reason, List<Long> unpaidMemberIds,
                                                LocalDateTime canceledAt) {
        return new SharedGroupOrderCanceledEvent(orderId, orderId * 10, reason, canceledAt, unpaidMemberIds);
    }
}
