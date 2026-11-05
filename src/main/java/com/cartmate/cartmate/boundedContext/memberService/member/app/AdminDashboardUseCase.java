package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.dto.AdminDashboardResponse;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.AdminDashboardResponse.AdminMemberStats;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.AdminDashboardResponse.AdminPaymentStats;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.AdminDashboardResponse.AdminRecentMember;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MemberCounts;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MemberPaymentServerClient;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminDashboardUseCase {
    private final MemberRepository memberRepository;
    private final MemberPaymentServerClient paymentServerClient;

    public AdminDashboardResponse getDashboard() {
        LocalDate today = LocalDate.now();

        // 회원 집계 1번 + 결제 집계 2번(결제·환불) + 최근 가입 회원 1번 = 조회 4번
        MemberCounts counts = memberRepository.countForDashboard(today.atStartOfDay());
        AdminMemberStats members = new AdminMemberStats(
                counts.total(), counts.joinedToday(), counts.buyers(), counts.sellers(), counts.suspended());

        MemberPaymentServerClient.PaymentStats paymentStats = paymentServerClient.getDailyPaymentStats(today);
        AdminPaymentStats payments = new AdminPaymentStats(
                paymentStats.paidCount(), paymentStats.paidAmount(), paymentStats.refundCount(), paymentStats.refundAmount());

        var recent = memberRepository.findTop5ByOrderByIdDesc().stream()
                .map(member -> new AdminRecentMember(member.getId(), member.getEmail(), member.getNickname(),
                        member.getRole().name(), member.getStatus().name(), member.getCreateDate()))
                .toList();

        return new AdminDashboardResponse(members, payments, recent, LocalDateTime.now());
    }
}