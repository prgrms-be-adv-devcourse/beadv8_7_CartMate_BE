package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseIdentityCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.dto.AbuseMemberCaseResponse;
import com.cartmate.cartmate.boundedContext.orderService.abuse.dto.AbuseMemberSummaryResponse;
import com.cartmate.cartmate.shared.abuse.dto.AbuseRestrictionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AbuseFacade {

    private final AbuseSignalReceiveUseCase abuseSignalReceiveUseCase;
    private final AbuseIdentityUseCase abuseIdentityUseCase;
    private final AbuseJudgeUseCase abuseJudgeUseCase;
    private final AbuseJudgeRetryUseCase abuseJudgeRetryUseCase;
    private final AbuseRestrictionQueryUseCase abuseRestrictionQueryUseCase;
    private final AbuseAccountRingSupport abuseAccountRingSupport;
    private final AbuseCaseQueryUseCase abuseCaseQueryUseCase;
    private final AbuseAdminActionUseCase abuseAdminActionUseCase;
    private final Clock clock;

    @Transactional
    public void receive(AbuseSignalCommand command) {
        abuseSignalReceiveUseCase.receive(command, LocalDateTime.now(clock));
    }

    @Transactional
    public void recordIdentity(AbuseIdentityCommand command) {
        abuseIdentityUseCase.record(command, LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void confirmLinks(Long memberId) {
        abuseAccountRingSupport.confirmLinksOfPending(memberId, LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int judgePending(Long memberId) {
        return abuseJudgeUseCase.judgePending(memberId, () -> LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordJudgeFailure(Long memberId, String error) {
        abuseJudgeRetryUseCase.recordFailure(memberId, error, LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public List<Long> findPendingMemberIds(int limit) {
        return abuseJudgeRetryUseCase.findPendingMemberIds(LocalDateTime.now(clock), limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public AbuseRestrictionStatus getRestriction(Long memberId) {
        return abuseRestrictionQueryUseCase.getRestriction(memberId, LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public List<AbuseMemberSummaryResponse> findFlaggedMembers(AbuseLevel minLevel, int limit) {
        return abuseCaseQueryUseCase.findFlaggedMembers(minLevel, limit, LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public AbuseMemberCaseResponse getMemberCase(Long memberId) {
        return abuseCaseQueryUseCase.getMemberCase(memberId, LocalDateTime.now(clock));
    }

    @Transactional
    public void restrictByAdmin(Long memberId, Long adminMemberId, int hours, String reason) {
        abuseAdminActionUseCase.restrict(memberId, adminMemberId, hours, reason, LocalDateTime.now(clock));
    }

    @Transactional
    public void releaseByAdmin(Long memberId, Long adminMemberId, String reason) {
        abuseAdminActionUseCase.release(memberId, adminMemberId, reason, LocalDateTime.now(clock));
    }

    @Transactional
    public int resetFailedSignalsByAdmin(Long memberId, Long adminMemberId, String reason) {
        return abuseAdminActionUseCase.resetFailedSignals(memberId, adminMemberId, reason, LocalDateTime.now(clock));
    }
}
