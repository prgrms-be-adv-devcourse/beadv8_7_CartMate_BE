package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.ReferenceType;
import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransaction;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransactionType;
import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletTransactionRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletPayoutUseCase {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final LedgerRecorder ledgerRecorder;

    // 판매 정산금을 판매자 지갑에 입금하고 정산 거래 내역을 저장한다. 같은 정산 ID로 다시 요청하면 처리하지 않는다.
    public void deposit(Long walletId, Long amount, Long payoutId, Long orderId) {
        validate(walletId, amount, payoutId, orderId);

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ServiceException(WalletErrorCode.WALLET_NOT_FOUND));

        Optional<WalletTransaction> existing = walletTransactionRepository.findByTypeAndOrderIdAndReferenceIdAndReferenceType(
                WalletTransactionType.SETTLEMENT, orderId, payoutId, ReferenceType.SETTLEMENT
        );

        if (existing.isPresent()) {
            WalletTransaction transaction = existing.get();

            if (!transaction.getWallet().getId().equals(walletId)) {
                throw new ServiceException(WalletErrorCode.WALLET_ACCESS_DENIED);
            }

            if (!transaction.getAmount().equals(amount)) {
                throw new ServiceException(WalletErrorCode.WALLET_TRANSACTION_AMOUNT_MISMATCH);
            }

            return;
        }

        wallet.deposit(amount);

        ledgerRecorder.record(
                wallet, WalletTransactionType.SETTLEMENT, orderId,
                payoutId, ReferenceType.SETTLEMENT, null, amount);
    }

    private void validate(Long walletId, Long amount, Long payoutId, Long orderId) {
        if (walletId == null) {
            throw new ServiceException(WalletErrorCode.WALLET_ID_REQUIRED);
        }

        if (amount == null || amount <= 0) {
            throw new ServiceException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }

        if (payoutId == null) {
            throw new ServiceException(WalletErrorCode.WALLET_PAYOUT_ID_REQUIRED);
        }

        if (orderId == null) {
            throw new ServiceException(WalletErrorCode.WALLET_ORDER_ID_REQUIRED);
        }
    }
}
