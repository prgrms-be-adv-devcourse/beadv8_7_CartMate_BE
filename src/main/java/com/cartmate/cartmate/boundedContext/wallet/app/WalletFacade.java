package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletBalanceResponse;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletChargeOrder;
import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletTransactionResponse;
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
    private final WalletChargeUseCase walletChargeUseCase;
    private final WalletPaymentUseCase walletPaymentUseCase;

    // 회원 지갑 생성을 요청한다.
    @Transactional
    public void createWallet(Long memberId) {
        walletCreateUseCase.createWallet(memberId);
    }

    // 회원의 지갑을 조회한다.
    @Transactional(readOnly = true)
    public WalletBalanceResponse getWallet(Long memberId) {
        return walletQueryUseCase.getWallet(memberId);
    }

    // 지갑 거래 내역을 페이지 조회한다.
    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getTransactions(
            Long walletId,
            Pageable pageable
    ) {
        return walletQueryUseCase.getTransactions(walletId, pageable);
    }

    // 잔액 충전과 거래 내역 저장을 하나의 트랜잭션으로 처리한다.
    @Transactional
    public void charge(Long walletId, Long amount) {
        walletChargeUseCase.charge(walletId, amount);
    }

    // 잔액 사용과 거래 내역 저장을 하나의 트랜잭션으로 처리한다.
    @Transactional
    public void use(Long walletId, Long amount) {
        walletPaymentUseCase.use(walletId, amount);
    }

    // 결제 환불에 따른 잔액 복구와 거래 내역 저장을 하나의 트랜잭션으로 처리한다.
    @Transactional
    public void refund(Long walletId, Long amount) {
        walletPaymentUseCase.refund(walletId, amount);
    }

    // 멱등성 키를 기준으로 충전 주문 생성을 요청한다.
    @Transactional
    public WalletChargeOrder chargeOrder(Long walletId, Long amount, String idempotencyKey) {
        return walletChargeUseCase.createChargeOrder(walletId, amount, idempotencyKey);
    }
}
