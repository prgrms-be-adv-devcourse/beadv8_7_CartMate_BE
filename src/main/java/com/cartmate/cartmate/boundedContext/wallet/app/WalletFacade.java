package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletBalanceResponse;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransaction;
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

    @Transactional
    public void createWallet(Long memberId) {
        walletCreateUseCase.createWallet(memberId);
    }

    @Transactional(readOnly = true)
    public WalletBalanceResponse getWallet(Long memberId) {
        return walletQueryUseCase.getWallet(memberId);
    }

    @Transactional(readOnly = true)
    public Page<WalletTransaction> getTransactions(
            Long walletId,
            Pageable pageable
    ) {
        return walletQueryUseCase.getTransactions(walletId, pageable);
    }
}
