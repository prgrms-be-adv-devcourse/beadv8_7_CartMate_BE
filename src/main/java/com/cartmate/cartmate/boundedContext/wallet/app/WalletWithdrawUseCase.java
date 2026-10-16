package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.*;
import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletWithdrawOrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletWithdrawUseCase {

    private final WalletWithdrawOrderRepository walletWithdrawOrderRepository;
    private final WalletRepository walletRepository;
    private final LedgerRecorder ledgerRecorder;

    // 대기 요청의 금액을 차감하고 출금 거래 내역과 완료 상태를 기록한다. 완료된 요청은 재처리하지 않는다.
    public void withdraw(Long walletId, Long withdrawOrderId) {
        WalletWithdrawOrder withdrawOrder = walletWithdrawOrderRepository.findById(withdrawOrderId)
                .orElseThrow(() -> new IllegalArgumentException("출금주문이 없습니다."));

        if (!withdrawOrder.getWallet().getId().equals(walletId)) {
            throw new IllegalArgumentException("지갑의 소유주가 아닙니다.");
        }

        if (withdrawOrder.getStatus() != WithdrawStatus.PENDING) {
            if (withdrawOrder.getStatus() == WithdrawStatus.COMPLETED) {
                return;
            }
            throw new IllegalArgumentException("현재상태에서는 출금을 완료할 수 없습니다.");
        }

        Long amount = withdrawOrder.getAmount();
        Wallet wallet = withdrawOrder.getWallet();
        wallet.withdraw(amount);
        withdrawOrder.complete();

        ledgerRecorder.record(wallet, WalletTransactionType.WITHDRAW, null,
                withdrawOrder.getId(), ReferenceType.WITHDRAW_ORDER, withdrawOrder.getIdempotencyKey(), amount);
    }

    // 출금 요청을 생성하거나 같은 멱등성 키·지갑·금액의 기존 요청을 반환한다. 새 요청일 때만 잔액을 확인한다.
    public WalletWithdrawOrder createWithdrawOrder(Long walletId, Long amount, String idempotencyKey) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ServiceException(WalletErrorCode.WALLET_NOT_FOUND));

        if (amount == null || amount <= 0) {
            throw new ServiceException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ServiceException(WalletErrorCode.WALLET_IDEMPOTENCY_KEY_REQUIRED);
        }

        Optional<WalletWithdrawOrder> existing = walletWithdrawOrderRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            WalletWithdrawOrder withdrawOrder = existing.get();

            if (!withdrawOrder.getWallet().getId().equals(walletId)) {
                throw new ServiceException(WalletErrorCode.WALLET_IDEMPOTENCY_CONFLICT);
            }

            if (!withdrawOrder.getAmount().equals(amount)) {
                throw new ServiceException(WalletErrorCode.WALLET_IDEMPOTENCY_CONFLICT);
            }
        }

        return existing.orElseGet(() -> {
            if (wallet.getBalance() < amount) {
                throw new ServiceException(WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
            }

            WalletWithdrawOrder withdrawOrder = WalletWithdrawOrder.create(
                    wallet, idempotencyKey, amount
            );
            return walletWithdrawOrderRepository.save(withdrawOrder);
        });
    }

    // 출금 요청 소유자를 확인하고 취소한다. 이미 취소된 요청은 재처리하지 않으며 잔액은 변경하지 않는다.
    public void cancel(Long walletId, Long withdrawOrderId) {
        if (walletId == null) {
            throw new IllegalArgumentException("지갑 ID는 필수입니다.");
        }
        if (withdrawOrderId == null) {
            throw new IllegalArgumentException("출금 요청 ID는 필수입니다.");
        }

        WalletWithdrawOrder withdrawOrder = walletWithdrawOrderRepository.findById(withdrawOrderId)
                .orElseThrow(() -> new IllegalArgumentException("출금 요청을 찾을 수 없습니다."));

        if (!withdrawOrder.getWallet().getId().equals(walletId)) {
            throw new IllegalArgumentException("해당 지갑에 접근할 권한이 없습니다.");
        }

        if (withdrawOrder.getStatus() == WithdrawStatus.CANCELED) {
            return;
        }

        withdrawOrder.cancel();
    }

    // 출금 요청 소유자를 확인하고 실패 처리한다. 이미 실패한 요청은 재처리하지 않으며 잔액은 변경하지 않는다.
    public void fail(Long walletId, Long withdrawOrderId) {
        if (walletId == null) {
            throw new IllegalArgumentException("지갑 ID는 필수입니다.");
        }
        if (withdrawOrderId == null) {
            throw new IllegalArgumentException("출금 요청 ID는 필수입니다.");
        }

        WalletWithdrawOrder withdrawOrder = walletWithdrawOrderRepository.findById(withdrawOrderId)
                .orElseThrow(() -> new IllegalArgumentException("출금 요청을 찾을 수 없습니다."));

        if (!withdrawOrder.getWallet().getId().equals(walletId)) {
            throw new IllegalArgumentException("해당 지갑에 접근할 권한이 없습니다.");
        }

        if (withdrawOrder.getStatus() == WithdrawStatus.FAILED) {
            return;
        }

        withdrawOrder.fail();
    }
}
