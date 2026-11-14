package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseAdminAction;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseAdminActionType;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseMemberState;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRestriction;
import com.cartmate.cartmate.boundedContext.orderService.abuse.exception.AbuseErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseAdminActionRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseRestrictionRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseSignalRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class AbuseAdminActionUseCase {

    static final int MIN_RESTRICTION_HOURS = 1;
    static final int MAX_RESTRICTION_HOURS = 720;

    private final AbuseMemberStateLocker abuseMemberStateLocker;
    private final AbuseRestrictionRepository abuseRestrictionRepository;
    private final AbuseSignalRepository abuseSignalRepository;
    private final AbuseAdminActionRepository abuseAdminActionRepository;

    void restrict(Long memberId, Long adminMemberId, int hours, String reason, LocalDateTime now) {
        if (hours < MIN_RESTRICTION_HOURS || hours > MAX_RESTRICTION_HOURS) {
            throw new ServiceException(AbuseErrorCode.ABUSE_INVALID_RESTRICTION_HOURS);
        }
        String validReason = validReason(reason);
        AbuseMemberState state = abuseMemberStateLocker.lock(memberId, now);
        LocalDateTime endsAt = now.plusHours(hours);
        AbuseRestriction restriction = activeRestriction(state, now)
                .map(active -> {
                    active.extendTo(endsAt);
                    return active;
                })
                .orElseGet(() -> abuseRestrictionRepository.save(AbuseRestriction.startManual(memberId, now, endsAt)));
        state.restrictManually(restriction.getId(), restriction.getEndsAt(), now);
        record(memberId, AbuseAdminActionType.RESTRICT, adminMemberId, validReason, restriction.getId(), null, now);
    }

    void release(Long memberId, Long adminMemberId, String reason, LocalDateTime now) {
        String validReason = validReason(reason);
        AbuseMemberState state = abuseMemberStateLocker.lock(memberId, now);
        Long restrictionId = activeRestriction(state, now)
                .map(active -> {
                    active.release(now);
                    return active.getId();
                })
                .orElse(null);
        state.release(now);
        record(memberId, AbuseAdminActionType.RELEASE, adminMemberId, validReason, restrictionId, null, now);
    }

    int resetFailedSignals(Long memberId, Long adminMemberId, String reason, LocalDateTime now) {
        String validReason = validReason(reason);
        abuseMemberStateLocker.lock(memberId, now);
        int reset = abuseSignalRepository.resetFailedToReceived(memberId, now);
        if (reset == 0) {
            throw new ServiceException(AbuseErrorCode.ABUSE_NO_FAILED_SIGNAL);
        }
        record(memberId, AbuseAdminActionType.RETRY_FAILED, adminMemberId, validReason, null, reset, now);
        return reset;
    }

    private Optional<AbuseRestriction> activeRestriction(AbuseMemberState state, LocalDateTime now) {
        return state.activeRestrictionId(now).flatMap(abuseRestrictionRepository::findById);
    }

    private void record(Long memberId, AbuseAdminActionType type, Long adminMemberId, String reason,
                        Long restrictionId, Integer affectedSignalCount, LocalDateTime now) {
        abuseAdminActionRepository.save(AbuseAdminAction.of(memberId, type, adminMemberId, reason, restrictionId,
                affectedSignalCount, now));
    }

    private static String validReason(String reason) {
        String trimmed = reason == null ? "" : reason.strip();
        if (trimmed.isEmpty() || trimmed.length() > AbuseAdminAction.REASON_LENGTH) {
            throw new ServiceException(AbuseErrorCode.ABUSE_ADMIN_REASON_REQUIRED);
        }
        return trimmed;
    }
}
