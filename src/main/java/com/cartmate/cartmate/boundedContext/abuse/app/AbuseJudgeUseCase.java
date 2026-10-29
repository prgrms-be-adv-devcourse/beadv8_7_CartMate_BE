package com.cartmate.cartmate.boundedContext.abuse.app;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseMemberState;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRestriction;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRule;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRuleResult;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseScoreThreshold;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseVerdict;
import com.cartmate.cartmate.boundedContext.abuse.exception.AbuseErrorCode;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseMemberStateRepository;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseRestrictionRepository;
import com.cartmate.cartmate.boundedContext.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
class AbuseJudgeUseCase {

    static final AbuseScoreThreshold SCORE_THRESHOLD = new AbuseScoreThreshold(30, 60, 100);

    private final AbuseSignalRepository abuseSignalRepository;
    private final AbuseMemberStateRepository abuseMemberStateRepository;
    private final AbuseRestrictionRepository abuseRestrictionRepository;
    private final List<AbuseRule> abuseRules;

    int judgePending(Long memberId, Supplier<LocalDateTime> clock) {
        AbuseMemberState state = lockMemberState(memberId, clock.get());
        LocalDateTime now = clock.get();
        List<AbuseSignal> pending = abuseSignalRepository
                .findByMemberIdAndStatusOrderByOccurredAtAscIdAsc(memberId, AbuseSignalStatus.RECEIVED);
        pending.forEach(signal -> judge(state, signal, now));
        return pending.size();
    }

    private AbuseMemberState lockMemberState(Long memberId, LocalDateTime now) {
        abuseMemberStateRepository.insertIfAbsent(memberId, now);
        return abuseMemberStateRepository.findForUpdate(memberId)
                .orElseThrow(() -> new ServiceException(AbuseErrorCode.ABUSE_MEMBER_STATE_NOT_FOUND));
    }

    private void judge(AbuseMemberState state, AbuseSignal signal, LocalDateTime now) {
        AbuseVerdict verdict = AbuseVerdict.of(evaluate(state.getMemberId(), now), SCORE_THRESHOLD);
        signal.judge(verdict, now);
        if (verdict.requiresRestriction()) {
            restrict(state, signal, now);
        }
        state.applyJudgment(verdict.level(), now);
    }

    private List<AbuseRuleResult> evaluate(Long memberId, LocalDateTime now) {
        return abuseRules.stream()
                .map(rule -> rule.evaluate(abuseSignalRepository.countInWindow(
                        memberId, rule.signalType(), now.minus(rule.threshold().window()), now)))
                .toList();
    }

    private void restrict(AbuseMemberState state, AbuseSignal signal, LocalDateTime now) {
        AbuseRestriction restriction = state.activeRestrictionId(now)
                .flatMap(id -> findActiveRestriction(state.getMemberId(), id))
                .orElse(null);
        if (restriction == null) {
            AbuseRestriction started = abuseRestrictionRepository.save(
                    AbuseRestriction.start(state.getMemberId(), signal.getId(), now));
            state.restrict(started.getId(), started.getEndsAt(), now);
            return;
        }
        if (restriction.extend(signal.getId(), now)) {
            state.restrict(restriction.getId(), restriction.getEndsAt(), now);
        }
    }

    private Optional<AbuseRestriction> findActiveRestriction(Long memberId, Long restrictionId) {
        Optional<AbuseRestriction> restriction = abuseRestrictionRepository.findById(restrictionId);
        if (restriction.isEmpty()) {
            log.warn("진행 중 제한 이력이 없어 새 제한을 시작함: memberId={}, restrictionId={}", memberId, restrictionId);
        }
        return restriction;
    }
}
