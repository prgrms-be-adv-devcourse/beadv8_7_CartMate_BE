package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseMemberState;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRestriction;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRule;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRuleResult;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseScoreThreshold;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseVerdict;
import com.cartmate.cartmate.boundedContext.orderService.abuse.exception.AbuseErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseMemberStateRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseRestrictionRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
class AbuseJudgeUseCase {

    static final AbuseScoreThreshold SCORE_THRESHOLD = new AbuseScoreThreshold(30, 60, 100);
    static final String ACCOUNT_RING_CODE = "ACCOUNT_RING";
    static final int RING_BOOST_PERCENT = 50;

    private final AbuseSignalRepository abuseSignalRepository;
    private final AbuseMemberStateRepository abuseMemberStateRepository;
    private final AbuseRestrictionRepository abuseRestrictionRepository;
    private final List<AbuseRule> abuseRules;
    private final AbuseAccountRingSupport abuseAccountRingSupport;

    int judgePending(Long memberId, Supplier<LocalDateTime> clock) {
        AbuseMemberState state = lockMemberState(memberId, clock.get());
        LocalDateTime now = clock.get();
        List<AbuseSignal> pending = abuseSignalRepository
                .findByMemberIdAndStatusOrderByOccurredAtAscIdAsc(memberId, AbuseSignalStatus.RECEIVED);
        Optional<AbuseRuleResult> ringResult = evaluateRing(memberId, now);
        pending.forEach(signal -> judge(state, signal, ringResult, now));
        return pending.size();
    }

    private AbuseMemberState lockMemberState(Long memberId, LocalDateTime now) {
        abuseMemberStateRepository.insertIfAbsent(memberId, now);
        return abuseMemberStateRepository.findForUpdate(memberId)
                .orElseThrow(() -> new ServiceException(AbuseErrorCode.ABUSE_MEMBER_STATE_NOT_FOUND));
    }

    private void judge(AbuseMemberState state, AbuseSignal signal, Optional<AbuseRuleResult> ringResult,
                       LocalDateTime now) {
        List<AbuseRuleResult> results = new ArrayList<>(evaluateRules(state.getMemberId(), now));
        ringResult.ifPresent(results::add);
        AbuseVerdict verdict = AbuseVerdict.of(results, SCORE_THRESHOLD);
        signal.judge(verdict, now);
        if (verdict.requiresRestriction()) {
            restrict(state, signal, now);
        }
        state.applyJudgment(verdict.level(), now);
    }

    private Optional<AbuseRuleResult> evaluateRing(Long memberId, LocalDateTime now) {
        Set<Long> ring = abuseAccountRingSupport.ringMembersOf(memberId);
        if (ring.isEmpty()) {
            return Optional.empty();
        }
        int othersScore = ring.stream()
                .mapToInt(other -> evaluateRules(other, now).stream().mapToInt(AbuseRuleResult::score).sum())
                .sum();
        int boost = Math.min(othersScore * RING_BOOST_PERCENT / 100, SCORE_THRESHOLD.restrictedScore());
        return Optional.of(new AbuseRuleResult(ACCOUNT_RING_CODE, ring.size(), boost));
    }

    private List<AbuseRuleResult> evaluateRules(Long memberId, LocalDateTime now) {
        return abuseRules.stream()
                .map(rule -> rule.evaluate(abuseSignalRepository.countInWindow(
                        memberId, rule.signalType(), now.minus(rule.weight().window()), now), SCORE_THRESHOLD))
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
