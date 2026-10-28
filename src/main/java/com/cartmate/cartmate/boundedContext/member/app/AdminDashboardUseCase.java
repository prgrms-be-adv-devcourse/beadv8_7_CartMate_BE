package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.MemberRole;
import com.cartmate.cartmate.boundedContext.member.domain.MemberStatus;
import com.cartmate.cartmate.boundedContext.member.dto.AdminDashboardResponse;
import com.cartmate.cartmate.boundedContext.member.dto.AdminDashboardResponse.AdminMemberStats;
import com.cartmate.cartmate.boundedContext.member.dto.AdminDashboardResponse.AdminPaymentStats;
import com.cartmate.cartmate.boundedContext.member.dto.AdminDashboardResponse.AdminRecentMember;
import com.cartmate.cartmate.boundedContext.member.out.AdminDashboardPaymentClient;
import com.cartmate.cartmate.boundedContext.member.out.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminDashboardUseCase {
    private final MemberRepository memberRepository;
    private final AdminDashboardPaymentClient paymentClient;

    public AdminDashboardResponse getDashboard() {
        LocalDate today = LocalDate.now();

        AdminMemberStats members = new AdminMemberStats(
                memberRepository.count(),
                memberRepository.countByCreateDateGreaterThanEqual(today.atStartOfDay()),
                memberRepository.countByRole(MemberRole.BUYER),
                memberRepository.countByRole(MemberRole.SELLER),
                memberRepository.countByStatus(MemberStatus.SUSPENDED));

        AdminDashboardPaymentClient.PaymentStats p = paymentClient.getDailyStats(today);
        AdminPaymentStats payments = new AdminPaymentStats(p.paidCount(), p.paidAmount(), p.refundCount(), p.refundAmount());

        var recent = memberRepository.findTop5ByOrderByIdDesc().stream()
                .map(m -> new AdminRecentMember(m.getId(), m.getEmail(), m.getNickname(),
                        m.getRole().name(), m.getStatus().name(), m.getCreateDate()))
                .toList();

        return new AdminDashboardResponse(members, payments, recent, LocalDateTime.now());
    }
}