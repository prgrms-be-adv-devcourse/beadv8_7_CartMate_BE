package com.cartmate.cartmate.boundedContext.memberService.member.out;

import com.cartmate.cartmate.boundedContext.paymentService.payment.app.PaymentFacade;
import com.cartmate.cartmate.boundedContext.paymentService.payment.dto.PaymentDailySummary;
import com.cartmate.cartmate.boundedContext.paymentService.payout.app.PayoutFacade;
import com.cartmate.cartmate.boundedContext.paymentService.wallet.app.WalletFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// 같은 앱 안에서는 결제·지갑·정산 Facade를 직접 부른다. 결제 서버를 나누면 HttpMemberPaymentServerClient로 바뀐다.
@Component
@ConditionalOnProperty(name = "cartmate.payment.client", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class FacadeMemberPaymentServerClient implements MemberPaymentServerClient {

    private final PaymentFacade paymentFacade;
    private final WalletFacade walletFacade;
    private final PayoutFacade payoutFacade;

    @Override
    public PaymentStats getDailyPaymentStats(LocalDate date) {
        PaymentDailySummary summary = paymentFacade.getDailySummary(date);
        return new PaymentStats(summary.paidCount(), summary.paidAmount(), summary.refundCount(), summary.refundAmount());
    }

    @Override
    public long getWalletBalance(Long memberId) {
        Long balance = walletFacade.getWallet(memberId).balance();
        return balance == null ? 0L : balance;
    }

    @Override
    public long countPendingPayouts(Long sellerId) {
        return payoutFacade.countPendingOfSeller(sellerId);
    }
}