package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.boundedContext.wallet.app.WalletFacade;
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
    public void use(Long walletId, Long amount, Long paymentId, Long orderId) {
        walletFacade.use(walletId, amount, paymentId, orderId);
    }

    @Override
    public void refund(Long walletId, Long amount, Long paymentId, Long orderId, Long refundId) {
        walletFacade.refund(walletId, amount, paymentId, orderId, refundId);
    }

    @Override
    public Long getBalance(Long memberId) {
        return walletFacade.getWallet(memberId).balance();
    }
}