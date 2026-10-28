package com.cartmate.cartmate.boundedContext.abuse.app;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseJudgment;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRestriction;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRuleResult;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseJudgmentRepository;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseRestrictionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AbuseJudgmentUseCaseTest {

    private static final Long MEMBER = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 12, 0);
    private static final AbuseSignalCommand SIGNAL =
            new AbuseSignalCommand(MEMBER, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED, "100", NOW);

    @Mock
    private AbuseJudgmentRepository abuseJudgmentRepository;

    @Mock
    private AbuseRestrictionRepository abuseRestrictionRepository;

    @InjectMocks
    private AbuseJudgmentUseCase abuseJudgmentUseCase;

    @Test
    @DisplayName("NONE 판정은 저장하지 않는다")
    void noneIsNotSaved() {
        abuseJudgmentUseCase.judge(SIGNAL, List.of(result(0, AbuseLevel.NONE, 0)), NOW);

        then(abuseJudgmentRepository).should(never()).save(any());
        then(abuseRestrictionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("1·2단계 판정은 기록만 남기고 제한하지 않는다")
    void suspiciousIsRecordedOnly() {
        abuseJudgmentUseCase.judge(SIGNAL, List.of(result(2, AbuseLevel.SUSPICIOUS, 80)), NOW);

        ArgumentCaptor<AbuseJudgment> captor = ArgumentCaptor.forClass(AbuseJudgment.class);
        then(abuseJudgmentRepository).should().save(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo(AbuseLevel.SUSPICIOUS);
        then(abuseRestrictionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("3단계 판정이고 진행 중인 제한이 없으면 24시간 제한을 만든다")
    void restrictedStartsRestriction() {
        given(abuseRestrictionRepository.findFirstByMemberIdAndReleasedAtIsNullAndEndsAtAfterOrderByEndsAtDesc(MEMBER, NOW))
                .willReturn(Optional.empty());

        abuseJudgmentUseCase.judge(SIGNAL, List.of(result(3, AbuseLevel.RESTRICTED, 120)), NOW);

        ArgumentCaptor<AbuseRestriction> captor = ArgumentCaptor.forClass(AbuseRestriction.class);
        then(abuseRestrictionRepository).should().save(captor.capture());
        assertThat(captor.getValue().getMemberId()).isEqualTo(MEMBER);
        assertThat(captor.getValue().getEndsAt()).isEqualTo(NOW.plus(AbuseRestriction.RESTRICTION_PERIOD));
    }

    @Test
    @DisplayName("3단계 판정이고 진행 중인 제한이 있으면 새로 만들지 않고 판정 시각부터 24시간으로 늘린다")
    void restrictedExtendsActiveRestriction() {
        AbuseRestriction active = AbuseRestriction.start(MEMBER, 10L, NOW.minusHours(5));
        given(abuseRestrictionRepository.findFirstByMemberIdAndReleasedAtIsNullAndEndsAtAfterOrderByEndsAtDesc(MEMBER, NOW))
                .willReturn(Optional.of(active));

        abuseJudgmentUseCase.judge(SIGNAL, List.of(result(3, AbuseLevel.RESTRICTED, 120)), NOW);

        then(abuseRestrictionRepository).should(never()).save(any());
        assertThat(active.getEndsAt()).isEqualTo(NOW.plus(AbuseRestriction.RESTRICTION_PERIOD));
    }

    @Test
    @DisplayName("같은 신호로 이미 판정했는지 저장소에서 확인한다")
    void isJudged() {
        given(abuseJudgmentRepository.existsByMemberIdAndSignalTypeAndSourceKey(
                MEMBER, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED, "100")).willReturn(true);

        assertThat(abuseJudgmentUseCase.isJudged(SIGNAL)).isTrue();
    }

    private AbuseRuleResult result(long count, AbuseLevel level, int score) {
        return new AbuseRuleResult("RULE", count, level, score);
    }
}
