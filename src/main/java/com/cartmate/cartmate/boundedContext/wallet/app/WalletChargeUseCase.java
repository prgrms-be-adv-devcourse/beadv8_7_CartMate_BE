package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.*;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletChargeOrderRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletChargeUseCase {
    private final WalletRepository walletRepository;
    private final WalletChargeOrderRepository walletChargeOrderRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    // 지갑 잔액을 충전하고 충전 거래 내역을 저장한다.
    public void charge(Long walletId, String orderNumber) {

        WalletChargeOrder chargeOrder = walletChargeOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("주문번호가 없습니다."));

        if (!chargeOrder.getWallet().getId().equals(walletId)) {
            throw new IllegalArgumentException("지갑의 소유자가 아닙니다.");
        }

        if (chargeOrder.getStatus() == ChargeStatus.COMPLETED) {
            return;
        }

        if (chargeOrder.getStatus() != ChargeStatus.PENDING) {
            throw new IllegalArgumentException("완료할 수 없는 주문입니다.");
        }

        Wallet wallet = chargeOrder.getWallet();
        Long amount = chargeOrder.getAmount();
        wallet.charge(amount);

        chargeOrder.complete();

        WalletTransaction transaction = WalletTransaction.create(
                wallet, WalletTransactionType.CHARGE, amount, wallet.getBalance(),
                chargeOrder.getId(), ReferenceType.CHARGE_ORDER
        );

        walletTransactionRepository.save(transaction);
    }

    // 충전 주문을 생성하거나, 같은 멱등성 키·지갑·금액의 기존 주문을 반환한다.
    public WalletChargeOrder createChargeOrder(Long walletId, Long amount, String idempotencyKey) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("지갑이 존재하지 않습니다."));

        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("충전금액은 0보다 커야합니다.");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("멱등성 키는 필수입니다.");
        }

        Optional<WalletChargeOrder> existing = walletChargeOrderRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            WalletChargeOrder chargeOrder = existing.get();

            if (!chargeOrder.getWallet().getId().equals(walletId)) {
                throw new IllegalArgumentException("멱등성키를 사용할 수 없습니다.");
            }

            if (!chargeOrder.getAmount().equals(amount)) {
                throw new IllegalArgumentException("같은 멱등성키에 다른금액을 사용할 수 없습니다.");
            }
        }

        return existing.orElseGet(() -> {
            WalletChargeOrder chargeOrder = WalletChargeOrder.create(
                    wallet, amount, idempotencyKey
            );
            return walletChargeOrderRepository.save(chargeOrder);
        });



    }
}
