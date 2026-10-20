package com.cartmate.cartmate.boundedContext.payment.out;

import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import com.cartmate.cartmate.boundedContext.wallet.app.WalletFacade;
import com.cartmate.cartmate.boundedContext.wallet.domain.HoldingPurchaseType;
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

    @Override
    public void use(Long walletId, Long amount, Long paymentId, Long orderId, PaymentType type) {
        walletFacade.use(walletId, amount, paymentId, orderId, toPurchaseType(type));
    }

    @Override
    public void refund(Long walletId, Long amount, Long paymentId, Long orderId, Long refundId) {
        walletFacade.refund(walletId, amount, paymentId, orderId, refundId);
    }

    // payment의 결제 유형을 wallet의 구매 유형으로 바꾼다.
    private HoldingPurchaseType toPurchaseType(PaymentType type) {
        return switch (type) {
            case INDIVIDUAL -> HoldingPurchaseType.INDIVIDUAL_GROUP;
            case GROUP_SHARE -> HoldingPurchaseType.SHARED_GROUP;
        };
    }
}