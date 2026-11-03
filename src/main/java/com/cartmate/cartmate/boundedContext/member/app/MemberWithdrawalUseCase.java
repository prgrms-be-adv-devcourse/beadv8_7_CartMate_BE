package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.domain.MemberRole;
import com.cartmate.cartmate.boundedContext.member.domain.MemberStatus;
import com.cartmate.cartmate.boundedContext.member.domain.Seller;
import com.cartmate.cartmate.boundedContext.member.dto.WithdrawalCheckResponse;
import com.cartmate.cartmate.boundedContext.member.dto.WithdrawalCheckResponse.WithdrawalBlocker;
import com.cartmate.cartmate.boundedContext.member.out.MemberRepository;
import com.cartmate.cartmate.boundedContext.member.out.SellerRepository;
import com.cartmate.cartmate.boundedContext.member.out.WithdrawalTradeClient;
import com.cartmate.cartmate.boundedContext.member.out.WithdrawalTradeClient.BuyerTrades;
import com.cartmate.cartmate.boundedContext.member.out.WithdrawalTradeClient.SellerTrades;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.member.event.MemberWithdrawalCanceledEvent;
import com.cartmate.cartmate.shared.member.event.MemberWithdrawalRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 회원 탈퇴 신청·취소 (1단계).
 *
 * <p>진행 중인 거래가 있으면 신청할 수 없다. 신청하면 탈퇴 대기가 되고, 유예 기간({@link #GRACE_DAYS}일) 동안
 * 로그인·조회·탈퇴 취소·예치금 환급 신청만 할 수 있다. 유예 기간이 지나면 탈퇴가 확정된다 (3단계).
 */
@Service
@RequiredArgsConstructor
class MemberWithdrawalUseCase {

    static final int GRACE_DAYS = 30;

    private final MemberRepository memberRepository;
    private final SellerRepository sellerRepository;
    private final WithdrawalTradeClient withdrawalTradeClient;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    WithdrawalCheckResponse check(Long memberId) {
        Member member = findMember(memberId);
        if (member.getStatus() == MemberStatus.WITHDRAWAL_PENDING) {
            return new WithdrawalCheckResponse(false, List.of(), member.getStatus().name(),
                    member.getWithdrawalRequestedAt(), member.getWithdrawalRequestedAt().plusDays(GRACE_DAYS));
        }
        List<WithdrawalBlocker> blockers = findBlockers(member);
        boolean canWithdraw = member.getStatus() == MemberStatus.ACTIVE && blockers.isEmpty();
        return new WithdrawalCheckResponse(canWithdraw, blockers, member.getStatus().name(), null, null);
    }

    WithdrawalCheckResponse request(Long memberId, String password) {
        Member member = findMember(memberId);
        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new ServiceException("400-PASSWORD_MISMATCH", "비밀번호가 일치하지 않습니다.");
        }

        List<WithdrawalBlocker> blockers = findBlockers(member);
        if (!blockers.isEmpty()) {
            throw new ServiceException("409-MEMBER_WITHDRAWAL_BLOCKED", blockers.get(0).message());
        }

        LocalDateTime now = LocalDateTime.now();
        member.requestWithdrawal(now);
        memberRepository.save(member);

        Long sellerId = sellerIdOf(member);
        eventPublisher.publishEvent(new MemberWithdrawalRequestedEvent(member.getId(), sellerId, now));
        return check(memberId);
    }

    WithdrawalCheckResponse cancel(Long memberId) {
        Member member = findMember(memberId);
        member.cancelWithdrawal();
        memberRepository.save(member);
        eventPublisher.publishEvent(new MemberWithdrawalCanceledEvent(member.getId()));
        return check(memberId);
    }

    List<Long> findPendingMemberIds() {
        return memberRepository.findIdsByStatus(MemberStatus.WITHDRAWAL_PENDING);
    }

    // 구매자로서 + (판매자면) 판매자로서 진행 중인 거래를 모은다
    private List<WithdrawalBlocker> findBlockers(Member member) {
        List<WithdrawalBlocker> blockers = new ArrayList<>();

        BuyerTrades buyer = withdrawalTradeClient.getBuyerTrades(member.getId());
        addIf(blockers, "PAYMENT_PENDING_ORDERS", "결제 대기 중인 주문이 있습니다.", buyer.paymentPendingOrders());
        addIf(blockers, "UNCONFIRMED_ORDERS", "구매확정 전인 주문이 있습니다. 구매확정 후 탈퇴할 수 있습니다.", buyer.unconfirmedDeliveries());
        addIf(blockers, "PAYING_SHARED_ORDERS", "분담금 결제 중인 공동 주문이 있습니다.", buyer.payingSharedOrders());
        addIf(blockers, "CLAIMS_IN_PROGRESS", "처리 중인 교환·반품 요청이 있습니다.", buyer.claimsInProgress());
        addIf(blockers, "ACTIVE_GROUP_PURCHASES", "모집 중인 공동구매에 참여하고 있습니다.", buyer.activeGroupPurchases());

        Long sellerId = sellerIdOf(member);
        if (sellerId != null) {
            SellerTrades seller = withdrawalTradeClient.getSellerTrades(sellerId);
            addIf(blockers, "SELLER_UNCONFIRMED_SALES", "발송·구매확정 전인 판매 건이 있습니다.", seller.unconfirmedDeliveries());
            addIf(blockers, "SELLER_CLAIMS_IN_PROGRESS", "처리 중인 교환·반품 요청이 있습니다(판매).", seller.claimsInProgress());
            addIf(blockers, "SELLER_PENDING_PAYOUTS", "지급 전인 정산이 있습니다.", seller.pendingPayouts());
        }
        return blockers;
    }

    private static void addIf(List<WithdrawalBlocker> blockers, String code, String message, long count) {
        if (count > 0) {
            blockers.add(new WithdrawalBlocker(code, message, count));
        }
    }

    private Long sellerIdOf(Member member) {
        if (member.getRole() != MemberRole.SELLER) {
            return null;
        }
        return sellerRepository.findByMemberId(member.getId()).map(Seller::getId).orElse(null);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ServiceException("404-MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다."));
    }
}