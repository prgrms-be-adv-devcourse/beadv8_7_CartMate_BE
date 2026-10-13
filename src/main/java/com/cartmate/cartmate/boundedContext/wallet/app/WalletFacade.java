package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletBalanceResponse;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletChargeOrder;
import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletTransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletFacade {
    private final WalletCreateUseCase walletCreateUseCase;
    private final WalletQueryUseCase walletQueryUseCase;
    private final WalletChargeUseCase walletChargeUseCase;
    private final WalletPaymentUseCase walletPaymentUseCase;

    @Transactional
    public void createWallet(Long memberId) {
        walletCreateUseCase.createWallet(memberId);
    }

    @Transactional(readOnly = true)
    public WalletBalanceResponse getWallet(Long memberId) {
        return walletQueryUseCase.getWallet(memberId);
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getTransactions(
            Long walletId,
            Pageable pageable
    ) {
        return walletQueryUseCase.getTransactions(walletId, pageable);
    }

    @Transactional
    public void charge(Long walletId, Long amount) {
        walletChargeUseCase.charge(walletId, amount);
    }

    @Transactional
    public void use(Long walletId, Long amount) {
        walletPaymentUseCase.use(walletId, amount);
    }

    @Transactional
    public void refund(Long walletId, Long amount) {
        walletPaymentUseCase.refund(walletId, amount);
    }

    @Transactional
    public WalletChargeOrder chargeOrder(Long walletId, Long amount) {
        return walletChargeUseCase.createChargeOrder(walletId, amount);
    }
}
