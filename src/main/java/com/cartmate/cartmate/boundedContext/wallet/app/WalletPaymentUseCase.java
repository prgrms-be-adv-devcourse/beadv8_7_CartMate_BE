package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.ReferenceType;
import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransaction;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransactionType;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletPaymentUseCase {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    // 지갑 잔액을 차감하고 사용 거래 내역을 저장한다.
    public void use(Long walletId, Long amount, Long referenceId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));

        if (referenceId == null) {
            throw new IllegalArgumentException("유효한 결제ID가 필요합니다.");
        }

        Optional<WalletTransaction> existing = walletTransactionRepository.findByTypeAndReferenceIdAndReferenceType(
                WalletTransactionType.USE, referenceId, ReferenceType.PAYMENT
        );

        if (existing.isPresent()) {
            WalletTransaction transaction = existing.get();

            if (!transaction.getWallet().getId().equals(walletId)) {
                throw new IllegalArgumentException("지갑 소유주가 아닙니다.");
            }

            if (!transaction.getAmount().equals(amount)) {
                throw new IllegalArgumentException("금액이 일치하지 않습니다.");
            }

            return;
        }

        wallet.use(amount);

        WalletTransaction walletTransaction = WalletTransaction.create(
                wallet, WalletTransactionType.USE, amount, wallet.getBalance(),
                referenceId, ReferenceType.PAYMENT
        );

        walletTransactionRepository.save(walletTransaction);
    }

    // 결제 취소 금액을 지갑에 복구하고 환불 거래 내역을 저장한다.
    public void refund(Long walletId, Long amount, Long referenceId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));

        if (referenceId == null) {
            throw new IllegalArgumentException("유효한 결제ID가 필요합니다.");
        }

        Optional<WalletTransaction> existing = walletTransactionRepository.findByTypeAndReferenceIdAndReferenceType(
                WalletTransactionType.REFUND, referenceId, ReferenceType.PAYMENT
        );

        if (existing.isPresent()) {
            WalletTransaction transaction = existing.get();

            if (!transaction.getWallet().getId().equals(walletId)) {
                throw new IllegalArgumentException("지갑ID가 일치하지 않습니다.");
            }

            if (!transaction.getAmount().equals(amount)) {
                throw new IllegalArgumentException("요청한 금액과 일치하지 않습니다.");
            }

            return;
        }

        wallet.refund(amount);

        WalletTransaction walletTransaction = WalletTransaction.create(
                wallet, WalletTransactionType.REFUND, amount, wallet.getBalance(),
                referenceId, ReferenceType.PAYMENT
        );

        walletTransactionRepository.save(walletTransaction);
    }
}
