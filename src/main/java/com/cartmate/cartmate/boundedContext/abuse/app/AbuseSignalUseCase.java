package com.cartmate.cartmate.boundedContext.abuse.app;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRule;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRuleResult;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseSignalCounter;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class AbuseSignalUseCase {

    private static final String DEADLINE_EXPIRED = "DEADLINE_EXPIRED";

    private final AbuseSignalCounter abuseSignalCounter;
    private final List<AbuseRule> abuseRules;
    private final AbuseJudgmentUseCase abuseJudgmentUseCase;

    void handleSharedGroupOrderCanceled(SharedGroupOrderCanceledEvent event, Long memberId, LocalDateTime now) {
        if (!DEADLINE_EXPIRED.equals(event.cancelReason())) {
            return;
        }
        handle(new AbuseSignalCommand(memberId, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                String.valueOf(event.sharedGroupOrderId()), event.canceledAt()), now);
    }

    void handle(AbuseSignalCommand signal, LocalDateTime now) {
        Optional<Duration> retention = retentionOf(signal.type());
        if (retention.isEmpty() || abuseJudgmentUseCase.isJudged(signal)) {
            return;
        }
        abuseSignalCounter.record(signal, retention.get());
        List<AbuseRuleResult> ruleResults = abuseRules.stream()
                .map(rule -> rule.evaluate(abuseSignalCounter.count(
                        signal.memberId(), rule.signalType(), now.minus(rule.threshold().window()))))
                .toList();
        abuseJudgmentUseCase.judge(signal, ruleResults, now);
    }

    private Optional<Duration> retentionOf(AbuseSignalType type) {
        return abuseRules.stream()
                .filter(rule -> rule.signalType() == type)
                .map(rule -> rule.threshold().window())
                .max(Comparator.naturalOrder());
    }
}
