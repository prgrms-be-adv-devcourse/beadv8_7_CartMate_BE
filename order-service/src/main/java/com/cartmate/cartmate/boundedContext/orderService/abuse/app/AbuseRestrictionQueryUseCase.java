package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseMemberState;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseMemberStateRepository;
import com.cartmate.cartmate.shared.abuse.dto.AbuseRestrictionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class AbuseRestrictionQueryUseCase {

    private final AbuseMemberStateRepository abuseMemberStateRepository;

    AbuseRestrictionStatus getRestriction(Long memberId, LocalDateTime now) {
        return abuseMemberStateRepository.findById(memberId)
                .map(state -> toStatus(state, now))
                .orElseGet(() -> AbuseRestrictionStatus.none(memberId));
    }

    private AbuseRestrictionStatus toStatus(AbuseMemberState state, LocalDateTime now) {
        return state.isRestricted(now)
                ? AbuseRestrictionStatus.restrictedUntil(state.getMemberId(), state.getRestrictedUntil())
                : AbuseRestrictionStatus.none(state.getMemberId());
    }
}
