package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseAccountLink;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseMemberState;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRestriction;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.orderService.abuse.dto.AbuseMemberCaseResponse;
import com.cartmate.cartmate.boundedContext.orderService.abuse.dto.AbuseMemberSummaryResponse;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseAccountLinkRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseMemberStateRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseRestrictionRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseSignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class AbuseCaseQueryUseCase {

    static final int MAX_LIST_SIZE = 100;
    static final int SIGNAL_HISTORY_SIZE = 100;

    private final AbuseMemberStateRepository abuseMemberStateRepository;
    private final AbuseSignalRepository abuseSignalRepository;
    private final AbuseRestrictionRepository abuseRestrictionRepository;
    private final AbuseAccountLinkRepository abuseAccountLinkRepository;
    private final AbuseAccountRingSupport abuseAccountRingSupport;

    List<AbuseMemberSummaryResponse> findFlaggedMembers(AbuseLevel minLevel, int limit, LocalDateTime now) {
        List<AbuseLevel> levels = Arrays.stream(AbuseLevel.values())
                .filter(level -> level.compareTo(minLevel) >= 0)
                .toList();
        int size = Math.clamp(limit, 1, MAX_LIST_SIZE);
        return abuseMemberStateRepository.findByCurrentLevelInOrderByLastJudgedAtDesc(levels, Limit.of(size)).stream()
                .map(state -> new AbuseMemberSummaryResponse(state.getMemberId(), state.getCurrentLevel(),
                        isRestricted(state.getRestrictedUntil(), now), state.getRestrictedUntil(), state.getLastJudgedAt()))
                .toList();
    }

    AbuseMemberCaseResponse getMemberCase(Long memberId, LocalDateTime now) {
        Optional<AbuseMemberState> state = abuseMemberStateRepository.findById(memberId);
        List<AbuseSignal> signals = abuseSignalRepository.findByMemberIdOrderByOccurredAtDescIdDesc(
                memberId, Limit.of(SIGNAL_HISTORY_SIZE));
        Optional<AbuseSignal> latestJudged = abuseSignalRepository
                .findFirstByMemberIdAndStatusOrderByJudgedAtDescIdDesc(memberId, AbuseSignalStatus.JUDGED);
        LocalDateTime restrictedUntil = state.map(AbuseMemberState::getRestrictedUntil).orElse(null);
        return new AbuseMemberCaseResponse(
                memberId,
                state.map(AbuseMemberState::getCurrentLevel).orElse(AbuseLevel.NONE),
                isRestricted(restrictedUntil, now),
                restrictedUntil,
                state.map(AbuseMemberState::getLastJudgedAt).orElse(null),
                latestJudged.map(AbuseSignal::getTotalScore).orElse(null),
                latestJudged.map(this::ruleScores).orElse(List.of()),
                signals.stream().map(this::signal).toList(),
                abuseRestrictionRepository.findByMemberIdOrderByStartedAtAsc(memberId).stream()
                        .map(restriction -> restriction(restriction, now))
                        .toList(),
                abuseAccountLinkRepository.findByMember(memberId).stream()
                        .map(link -> link(link, memberId))
                        .toList(),
                ring(memberId)
        );
    }

    private List<AbuseMemberCaseResponse.RuleScore> ruleScores(AbuseSignal signal) {
        return signal.getRuleResults() == null ? List.of() : signal.getRuleResults().stream()
                .map(result -> new AbuseMemberCaseResponse.RuleScore(result.ruleCode(), result.signalCount(), result.score()))
                .toList();
    }

    private AbuseMemberCaseResponse.Signal signal(AbuseSignal signal) {
        return new AbuseMemberCaseResponse.Signal(signal.getId(), signal.getSignalType(), signal.getSourceKey(),
                signal.getContextKey(), signal.getOccurredAt(), signal.getStatus(), signal.getLevel(),
                signal.getTotalScore(), signal.getAttemptCount(), signal.getLastError());
    }

    private AbuseMemberCaseResponse.Restriction restriction(AbuseRestriction restriction, LocalDateTime now) {
        return new AbuseMemberCaseResponse.Restriction(restriction.getId(), restriction.getStartedAt(),
                restriction.getEndsAt(), restriction.getExtendedCount(), restriction.getLastSignalId(),
                restriction.getEndsAt().isAfter(now));
    }

    private AbuseMemberCaseResponse.Link link(AbuseAccountLink link, Long memberId) {
        return new AbuseMemberCaseResponse.Link(link.otherThan(memberId), link.getIdentityType(),
                link.getSharedContextCount(), link.getConfirmedAt());
    }

    private List<AbuseMemberCaseResponse.RingMember> ring(Long memberId) {
        List<Long> ringIds = List.copyOf(abuseAccountRingSupport.ringMembersOf(memberId));
        Map<Long, AbuseMemberState> states = abuseMemberStateRepository.findAllById(ringIds).stream()
                .collect(Collectors.toMap(AbuseMemberState::getMemberId, Function.identity()));
        return ringIds.stream()
                .map(id -> Optional.ofNullable(states.get(id))
                        .map(s -> new AbuseMemberCaseResponse.RingMember(id, s.getCurrentLevel(), s.getRestrictedUntil()))
                        .orElse(new AbuseMemberCaseResponse.RingMember(id, AbuseLevel.NONE, null)))
                .toList();
    }

    private static boolean isRestricted(LocalDateTime restrictedUntil, LocalDateTime now) {
        return restrictedUntil != null && restrictedUntil.isAfter(now);
    }
}
