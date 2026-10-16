package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.domain.MemberRole;
import com.cartmate.cartmate.boundedContext.member.domain.MemberStatus;
import com.cartmate.cartmate.boundedContext.member.domain.MemberSuspension;
import com.cartmate.cartmate.boundedContext.member.domain.SuspensionReleaseType;
import com.cartmate.cartmate.boundedContext.member.dto.AdminBulkStatusRequest;
import com.cartmate.cartmate.boundedContext.member.dto.AdminBulkStatusResponse;
import com.cartmate.cartmate.boundedContext.member.dto.AdminSuspendRequest;
import com.cartmate.cartmate.boundedContext.member.out.MemberRepository;
import com.cartmate.cartmate.boundedContext.member.out.MemberSuspensionRepository;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.member.event.MemberStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminMemberStatusUseCase {
    private final MemberRepository memberRepository;
    private final MemberSuspensionRepository memberSuspensionRepository;
    private final EventPublisher eventPublisher;

    public void suspend(Long adminId, Long memberId, AdminSuspendRequest request) {
        Member member = getMember(memberId);

        if (member.getId().equals(adminId)) {
            throw new ServiceException("400-ADMIN_CANNOT_SUSPEND_SELF", "자기 자신은 정지할 수 없습니다.");
        }
        if (member.getRole() == MemberRole.ADMIN) {
            throw new ServiceException("400-ADMIN_CANNOT_SUSPEND_ADMIN", "관리자 계정은 정지할 수 없습니다.");
        }

        member.suspend(); // 이미 정지·탈퇴면 여기서 예외

        MemberSuspension suspension = memberSuspensionRepository.save(MemberSuspension.create(
                member, request.reasonType(), request.reasonDetail().trim(),
                request.duration(), adminId, LocalDateTime.now()));

        eventPublisher.publish(new MemberStatusChangedEvent(
                member.getId(), member.getRole().name(), MemberStatus.SUSPENDED.name(), suspension.getSuspendedUntil()));
    }

    public void activate(Long memberId, SuspensionReleaseType releaseType) {
        Member member = getMember(memberId);
        member.activate(); // 정지 상태가 아니면 여기서 예외

        LocalDateTime now = LocalDateTime.now();
        memberSuspensionRepository.findFirstByMemberIdAndReleasedAtIsNullOrderBySuspendedAtDesc(memberId)
                .ifPresent(s -> s.release(releaseType, now));

        eventPublisher.publish(new MemberStatusChangedEvent(
                member.getId(), member.getRole().name(), MemberStatus.ACTIVE.name(), null));
    }

    public AdminBulkStatusResponse bulk(Long adminId, AdminBulkStatusRequest request) {
        boolean suspend = "SUSPEND".equals(request.action());
        if (suspend && request.suspend() == null) {
            throw new ServiceException("400-COMMON_INVALID_INPUT", "정지 사유와 기간을 입력해주세요.");
        }

        int success = 0;
        List<AdminBulkStatusResponse.Failure> failures = new ArrayList<>();

        for (Long memberId : request.memberIds().stream().distinct().toList()) {
            try {
                if (suspend) {
                    suspend(adminId, memberId, request.suspend());
                } else {
                    activate(memberId, SuspensionReleaseType.MANUAL);
                }
                success++;
            } catch (ServiceException e) {
                failures.add(new AdminBulkStatusResponse.Failure(memberId, e.getMsg()));
            }
        }
        return new AdminBulkStatusResponse(success, failures);
    }

    // 기간이 지난 정지를 해제. 스케줄러가 호출
    public int releaseExpired() {
        LocalDateTime now = LocalDateTime.now();
        List<MemberSuspension> expired = memberSuspensionRepository.findByReleasedAtIsNullAndSuspendedUntilBefore(now);

        for (MemberSuspension s : expired) {
            Member member = s.getMember();
            s.release(SuspensionReleaseType.EXPIRED, now);
            if (member.getStatus() == MemberStatus.SUSPENDED) {
                member.activate();
                eventPublisher.publish(new MemberStatusChangedEvent(
                        member.getId(), member.getRole().name(), MemberStatus.ACTIVE.name(), null));
            }
        }
        return expired.size();
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceException("404-MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다."));
    }
}