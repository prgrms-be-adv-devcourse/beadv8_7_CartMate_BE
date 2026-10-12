package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransaction;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransactionType;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletChargeUseCase {
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public void charge(Long walletId, Long amount) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));

        wallet.charge(amount);

        WalletTransaction walletTransaction = WalletTransaction.create(
                wallet, WalletTransactionType.CHARGE, amount, wallet.getBalance(),
                null, null
        );

        walletTransactionRepository.save(walletTransaction);
    }
}
