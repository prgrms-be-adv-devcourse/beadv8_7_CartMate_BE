package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.domain.MemberSuspension;
import com.cartmate.cartmate.boundedContext.member.dto.AdminMemberDetailResponse;
import com.cartmate.cartmate.boundedContext.member.dto.MyPageDashboardResponse;
import com.cartmate.cartmate.boundedContext.member.out.MemberRepository;
import com.cartmate.cartmate.boundedContext.member.out.MemberSuspensionRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminMemberDetailUseCase {
    private final MemberRepository memberRepository;
    private final MemberSuspensionRepository memberSuspensionRepository;
    private final MyPageDashboardUseCase myPageDashboardUseCase;

    public AdminMemberDetailResponse get(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceException("404-MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다."));

        List<MemberSuspension> history = memberSuspensionRepository.findByMemberIdOrderBySuspendedAtDesc(memberId);

        // 처리 관리자 닉네임을 한 번에 조회
        Map<Long, String> adminNicknames = memberRepository
                .findAllById(history.stream().map(MemberSuspension::getAdminId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Member::getId, Member::getNickname, (a, b) -> a));

        List<AdminMemberDetailResponse.Suspension> suspensions = history.stream()
                .map(s -> toDto(s, adminNicknames))
                .toList();

        AdminMemberDetailResponse.Suspension current = history.stream()
                .filter(s -> s.getReleasedAt() == null)
                .findFirst()
                .map(s -> toDto(s, adminNicknames))
                .orElse(null);

        MyPageDashboardResponse activity = myPageDashboardUseCase.getDashboard(memberId);

        return new AdminMemberDetailResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getPhone(),
                member.getBirthDate(),
                member.getRole().name(),
                member.getStatus().name(),
                member.getCreateDate(),
                activity.wallet().balance(),
                activity.groupPurchases().size(),
                activity.recentOrders().size(),
                current,
                suspensions
        );
    }

    private AdminMemberDetailResponse.Suspension toDto(MemberSuspension s, Map<Long, String> adminNicknames) {
        return new AdminMemberDetailResponse.Suspension(
                s.getId(),
                s.getReasonType().name(),
                s.getReasonDetail(),
                s.getSuspendedAt(),
                s.getSuspendedUntil(),
                s.getReleasedAt(),
                s.getReleaseType() == null ? null : s.getReleaseType().name(),
                s.getAdminId(),
                adminNicknames.getOrDefault(s.getAdminId(), "알 수 없음")
        );
    }
}