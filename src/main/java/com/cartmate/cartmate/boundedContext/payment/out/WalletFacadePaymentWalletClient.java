package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import com.cartmate.cartmate.boundedContext.wallet.app.WalletFacade;
import com.cartmate.cartmate.shared.wallet.WalletPurchaseType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// wallet 모듈과 닿는 유일한 곳
@Component
@RequiredArgsConstructor
public class WalletFacadePaymentWalletClient implements PaymentWalletClient {
    private final WalletFacade walletFacade;

    @Override
    public Long getWalletId(Long memberId) {
        return walletFacade.getWallet(memberId).walletId();
    }

    @Override
    public Long getBalance(Long memberId) {
        return walletFacade.getWallet(memberId).balance();
    }

    // PaymentType과 WalletPurchaseType은 같은 값을 쓴다 (한쪽에 값을 추가하면 다른 쪽도 추가)
    @Override
    public void use(Long walletId, Long amount, Long paymentId, Long orderId, PaymentType type) {
        walletFacade.use(walletId, amount, paymentId, orderId, WalletPurchaseType.valueOf(type.name()));
    }

    @Override
    public void refund(Long walletId, Long amount, Long paymentId, Long orderId, Long refundId) {
        walletFacade.refund(walletId, amount, paymentId, orderId, refundId);
    }
}