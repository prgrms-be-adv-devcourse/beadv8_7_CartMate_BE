package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseMemberState;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseMemberStateRepository;
import com.cartmate.cartmate.boundedContext.orderService.shared.abuse.dto.AbuseRestrictionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class AbuseRestrictionQueryUseCase {

    private final AbuseMemberStateRepository abuseMemberStateRepository;

    AbuseRestrictionStatus getRestriction(Long memberId, LocalDateTime now) {
        return abuseMemberStateRepository.findById(memberId)
                .map(state -> toStatus(state, now))
                .orElseGet(() -> AbuseRestrictionStatus.none(memberId));
    }

    Map<Long, AbuseRestrictionStatus> getRestrictions(Collection<Long> memberIds, LocalDateTime now) {
        Map<Long, AbuseMemberState> states = abuseMemberStateRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(AbuseMemberState::getMemberId, Function.identity()));
        Map<Long, AbuseRestrictionStatus> result = new LinkedHashMap<>();
        memberIds.forEach(memberId -> result.put(memberId, states.containsKey(memberId)
                ? toStatus(states.get(memberId), now)
                : AbuseRestrictionStatus.none(memberId)));
        return result;
    }

    private AbuseRestrictionStatus toStatus(AbuseMemberState state, LocalDateTime now) {
        return state.isRestricted(now)
                ? AbuseRestrictionStatus.restrictedUntil(state.getMemberId(), state.getRestrictedUntil())
                : AbuseRestrictionStatus.none(state.getMemberId());
    }
}
