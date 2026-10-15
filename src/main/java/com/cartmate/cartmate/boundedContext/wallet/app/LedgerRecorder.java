package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.ReferenceType;
import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransaction;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransactionType;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LedgerRecorder {

    private final WalletTransactionRepository walletTransactionRepository;

    public void record(
            Wallet wallet,
            WalletTransactionType transactionType,
            Long orderId,
            Long referenceId,
            ReferenceType referenceType,
            Long amount
    ) {
        WalletTransaction walletTransaction = WalletTransaction.create(
                wallet, transactionType, orderId, referenceId, referenceType, amount, wallet.getBalance()
        );

        walletTransactionRepository.save(walletTransaction);
    }
}
