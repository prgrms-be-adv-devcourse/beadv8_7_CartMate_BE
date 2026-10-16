package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.*;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletChargeOrderRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;

@Service
@RequiredArgsConstructor
public class WalletChargeUseCase {
    private final WalletRepository walletRepository;
    private final WalletChargeOrderRepository walletChargeOrderRepository;
    private final LedgerRecorder ledgerRecorder;

    // 대기 주문의 금액을 충전하고 거래 내역과 완료 상태를 기록한다. 완료된 주문은 재처리하지 않는다.
    public void charge(Long walletId, String orderNumber, String paymentKey, Long amount) {

        WalletChargeOrder chargeOrder = walletChargeOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ServiceException(WalletErrorCode.WALLET_CHARGE_ORDER_NOT_FOUND));

        if (!chargeOrder.getWallet().getId().equals(walletId)) {
            throw new ServiceException(WalletErrorCode.WALLET_ACCESS_DENIED);
        }

        if (paymentKey == null || paymentKey.isBlank()) {
            throw new ServiceException(WalletErrorCode.WALLET_PAYMENT_KEY_REQUIRED);
        }

        if (!chargeOrder.getAmount().equals(amount)) {
            throw new ServiceException(WalletErrorCode.WALLET_CHARGE_AMOUNT_MISMATCH);
        }

        if (chargeOrder.getStatus() == ChargeStatus.COMPLETED) {
            if (!paymentKey.equals(chargeOrder.getPaymentKey())) {
                throw new ServiceException(WalletErrorCode.WALLET_PAYMENT_KEY_MISMATCH);
            }
            return;
        }

        if (chargeOrder.getStatus() != ChargeStatus.PENDING) {
            throw new ServiceException(WalletErrorCode.WALLET_CHARGE_NOT_COMPLETABLE);
        }

        Wallet wallet = chargeOrder.getWallet();
        wallet.charge(amount);

        chargeOrder.complete(paymentKey);

        ledgerRecorder.record(wallet, WalletTransactionType.CHARGE, null,
                chargeOrder.getId(), ReferenceType.CHARGE_ORDER, chargeOrder.getIdempotencyKey(), amount);
    }

    // 충전 주문을 생성하거나, 같은 멱등성 키·지갑·금액의 기존 주문을 반환한다.
    public WalletChargeOrder createChargeOrder(Long walletId, Long amount, String idempotencyKey) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ServiceException(WalletErrorCode.WALLET_NOT_FOUND));

        if (amount == null || amount <= 0) {
            throw new ServiceException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ServiceException(WalletErrorCode.WALLET_IDEMPOTENCY_KEY_REQUIRED);
        }

        Optional<WalletChargeOrder> existing = walletChargeOrderRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            WalletChargeOrder chargeOrder = existing.get();

            if (!chargeOrder.getWallet().getId().equals(walletId)) {
                throw new ServiceException(WalletErrorCode.WALLET_IDEMPOTENCY_CONFLICT);
            }

            if (!chargeOrder.getAmount().equals(amount)) {
                throw new ServiceException(WalletErrorCode.WALLET_IDEMPOTENCY_CONFLICT);
            }
        }

        return existing.orElseGet(() -> {
            WalletChargeOrder chargeOrder = WalletChargeOrder.create(
                    wallet, amount, idempotencyKey
            );
            return walletChargeOrderRepository.save(chargeOrder);
        });

    }

    // 주문 소유자를 확인하고 취소를 요청한다. 이미 취소된 주문은 재처리하지 않는다.
    public void cancel(Long walletId, String orderNumber) {
        WalletChargeOrder chargeOrder = walletChargeOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ServiceException(WalletErrorCode.WALLET_CHARGE_ORDER_NOT_FOUND));

        if (!chargeOrder.getWallet().getId().equals(walletId)) {
            throw new ServiceException(WalletErrorCode.WALLET_ACCESS_DENIED);
        }

        if (chargeOrder.getStatus() == ChargeStatus.CANCELED) {
            return;
        }

        chargeOrder.cancel();
    }

    // 주문 소유자를 확인하고 실패 처리를 요청한다. 이미 실패한 주문은 재처리하지 않는다.
    public void fail(Long walletId, String orderNumber) {
        WalletChargeOrder chargeOrder = walletChargeOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ServiceException(WalletErrorCode.WALLET_CHARGE_ORDER_NOT_FOUND));

        if (!chargeOrder.getWallet().getId().equals(walletId)) {
            throw new ServiceException(WalletErrorCode.WALLET_ACCESS_DENIED);
        }

        if (chargeOrder.getStatus() == ChargeStatus.FAILED) {
            return;
        }

        chargeOrder.fail();
    }
}
