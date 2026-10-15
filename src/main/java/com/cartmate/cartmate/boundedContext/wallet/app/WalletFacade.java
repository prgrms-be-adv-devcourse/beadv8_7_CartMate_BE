package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletBalanceResponse;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletChargeOrder;
import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletTransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletFacade {
    private final WalletCreateUseCase walletCreateUseCase;
    private final WalletQueryUseCase walletQueryUseCase;
    private final WalletChargeUseCase walletChargeUseCase;
    private final WalletPaymentUseCase walletPaymentUseCase;
    private final WalletSettlementUseCase walletSettlementUseCase;

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

    // 주문 금액 충전·거래 내역 저장·주문 완료를 하나의 트랜잭션으로 처리한다. 완료 주문은 재충전하지 않는다.
    @Transactional
    public void charge(Long walletId, String orderNumber, String paymentKey, Long amount) {
        walletChargeUseCase.charge(walletId, orderNumber, paymentKey, amount);
    }

    // 결제 ID 기준으로 중복 사용을 확인하고 잔액차감·홀딩생성·거래내역저장을 하나의 트랜잭션으로 처리한다.
    @Transactional
    public void use(Long walletId, Long amount, Long orderId, Long paymentId) {
        walletPaymentUseCase.use(walletId, amount, orderId, paymentId);
    }

    // 결제 ID 기준으로 중복 환불을 확인하고 홀딩취소·잔액복구·환불거래저장을 하나의 트랜잭션으로 처리한다.
    @Transactional
    public void refund(Long walletId, Long amount, Long orderId, Long paymentId) {
        walletPaymentUseCase.refund(walletId, amount, orderId, paymentId);
    }

    // 충전 주문을 생성하거나 같은 멱등성 키·지갑·금액의 기존 주문을 반환한다.
    @Transactional
    public WalletChargeOrder chargeOrder(Long walletId, Long amount, String idempotencyKey) {
        return walletChargeUseCase.createChargeOrder(walletId, amount, idempotencyKey);
    }

    // 대기 중인 충전 주문을 취소한다. 이미 취소된 주문은 재처리하지 않으며 잔액은 변경하지 않는다.
    @Transactional
    public void cancel(Long walletId, String orderNumber) {
        walletChargeUseCase.cancel(walletId, orderNumber);
    }

    // 대기 중인 충전 주문을 실패 처리한다. 이미 실패한 주문은 재처리하지 않으며 잔액은 변경하지 않는다.
    @Transactional
    public void fail(Long walletId, String orderNumber) {
        walletChargeUseCase.fail(walletId, orderNumber);
    }

    // 주문별 홀딩의 정산 ID 기록과 정산 완료 전환을 하나의 트랜잭션으로 처리한다. 같은 정산으로 완료된 홀딩은 건너뛴다.
    @Transactional
    public void settle(List<Long> orderIds, Long settlementId) {
        walletSettlementUseCase.settle(orderIds, settlementId);
    }
}
