package com.cartmate.cartmate.boundedContext.abuse.app;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseJudgment;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRestriction;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRuleResult;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseScoreThreshold;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseJudgmentRepository;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
class AbuseJudgmentUseCase {

    static final AbuseScoreThreshold SCORE_THRESHOLD = new AbuseScoreThreshold(30, 60, 100);

    private final AbuseJudgmentRepository abuseJudgmentRepository;
    private final AbuseRestrictionRepository abuseRestrictionRepository;

    boolean isJudged(AbuseSignal signal) {
        return abuseJudgmentRepository.existsByMemberIdAndSignalTypeAndSourceKey(
                signal.memberId(), signal.type(), signal.sourceKey());
    }

    void judge(AbuseSignal signal, List<AbuseRuleResult> ruleResults, LocalDateTime judgedAt) {
        AbuseJudgment judgment = AbuseJudgment.judge(signal, ruleResults, SCORE_THRESHOLD, judgedAt);
        if (judgment.isNone()) {
            return;
        }
        abuseJudgmentRepository.save(judgment);
        if (judgment.requiresRestriction()) {
            restrict(judgment);
        }
    }

    private void restrict(AbuseJudgment judgment) {
        abuseRestrictionRepository
                .findFirstByMemberIdAndReleasedAtIsNullAndEndsAtAfterOrderByEndsAtDesc(
                        judgment.getMemberId(), judgment.getJudgedAt())
                .ifPresentOrElse(
                        restriction -> restriction.extend(judgment.getId(), judgment.getJudgedAt()),
                        () -> abuseRestrictionRepository.save(
                                AbuseRestriction.start(judgment.getMemberId(), judgment.getId(), judgment.getJudgedAt()))
                );
    }
}
