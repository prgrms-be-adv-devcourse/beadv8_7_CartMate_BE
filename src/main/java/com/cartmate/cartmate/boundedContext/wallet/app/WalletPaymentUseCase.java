package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.*;
import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.boundedContext.wallet.out.HoldingRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletTransactionRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
//TODO: Payment 연동 시 전체 결제·환불 트랜잭션 바깥에서 충돌 처리
public class WalletPaymentUseCase {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final HoldingRepository holdingRepository;
    private final LedgerRecorder ledgerRecorder;

    // 지갑 잔액을 차감하고 사용 거래 내역을 저장한다.
    public void use(Long walletId, Long amount, Long paymentId, Long orderId) {
        validatePaymentRequest(walletId, amount, paymentId, orderId);

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ServiceException(WalletErrorCode.WALLET_NOT_FOUND));

        Optional<WalletTransaction> existing = walletTransactionRepository.findByTypeAndOrderIdAndReferenceIdAndReferenceType(
                WalletTransactionType.USE, orderId, paymentId, ReferenceType.PAYMENT
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

        wallet.use(amount);

        Holding holding = Holding.create(
                wallet, orderId, amount, paymentId
        );

        holdingRepository.save(holding);

        ledgerRecorder.record(
                wallet, WalletTransactionType.USE, orderId,
                paymentId, ReferenceType.PAYMENT, null, amount);
    }

    // 결제 취소 금액을 지갑에 복구하고 환불 거래 내역을 저장한다. 같은 결제에 대해 refundId 단위로 여러 번 부분 환불할 수 있다.
    public void refund(Long walletId, Long amount, Long paymentId, Long orderId, Long refundId) {
        validatePaymentRequest(walletId, amount, paymentId, orderId);

        if (refundId == null) {
            throw new ServiceException(WalletErrorCode.WALLET_REFUND_ID_REQUIRED);
        }

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ServiceException(WalletErrorCode.WALLET_NOT_FOUND));

        Optional<WalletTransaction> existing = walletTransactionRepository.findByTypeAndOrderIdAndReferenceIdAndReferenceType(
                WalletTransactionType.REFUND, orderId, refundId, ReferenceType.REFUND
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

        Holding holding = holdingRepository.findByOrderIdAndPaymentId(orderId, paymentId)
                .orElseThrow(() -> new ServiceException(WalletErrorCode.WALLET_HOLDING_NOT_FOUND));

        if (!holding.getWallet().getId().equals(walletId)) {
            throw new ServiceException(WalletErrorCode.WALLET_ACCESS_DENIED);
        }

        holding.addRefund(amount);

        wallet.refund(amount);

        holdingRepository.save(holding);

        ledgerRecorder.record(
                wallet, WalletTransactionType.REFUND, orderId,
                refundId, ReferenceType.REFUND, null, amount);
    }

    private void validatePaymentRequest(
            Long walletId, Long amount, Long paymentId, Long orderId
    ) {
        if (walletId == null) {
            throw new ServiceException(WalletErrorCode.WALLET_ID_REQUIRED);
        }

        if (amount == null || amount <= 0) {
            throw new ServiceException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }

        if (paymentId == null) {
            throw new ServiceException(WalletErrorCode.WALLET_PAYMENT_ID_REQUIRED);
        }

        if (orderId == null) {
            throw new ServiceException(WalletErrorCode.WALLET_ORDER_ID_REQUIRED);
        }
    }
}
