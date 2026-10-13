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
public class WalletPaymentUseCase {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    // 지갑 잔액을 차감하고 사용 거래 내역을 저장한다.
    public void use(Long walletId, Long amount) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));

        wallet.use(amount);

        WalletTransaction walletTransaction = WalletTransaction.create(
                wallet, WalletTransactionType.USE, amount, wallet.getBalance(),
                null, null
        );

        walletTransactionRepository.save(walletTransaction);
    }

    // 결제 취소 금액을 지갑에 복구하고 환불 거래 내역을 저장한다.
    public void refund(Long walletId, Long amount) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));

        wallet.refund(amount);

        WalletTransaction walletTransaction = WalletTransaction.create(
                wallet, WalletTransactionType.REFUND, amount, wallet.getBalance(),
                null, null
        );

        walletTransactionRepository.save(walletTransaction);
    }
}
