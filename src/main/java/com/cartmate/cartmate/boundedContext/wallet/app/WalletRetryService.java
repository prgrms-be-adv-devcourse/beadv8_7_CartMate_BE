package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.WalletChargeOrder;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletWithdrawOrder;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletChargeOrderRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletWithdrawOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// TODO: 추후 분산 락 도입 시 동시성 제어 및 재시도 정책 재검토
public class WalletRetryService {

    private final WalletFacade walletFacade;
    private final WalletChargeOrderRepository walletChargeOrderRepository;
    private final WalletWithdrawOrderRepository walletWithdrawOrderRepository;

    // 낙관적 락 충돌로 롤백된 충전 처리를 최대 3회 재시도한다.
    @Retryable(
            value = {
                    OptimisticLockingFailureException.class, //낙관적 락 충돌
                    CannotAcquireLockException.class    // DB 락 획득 실패(데드락 등)
            },
            maxRetries = 3,
            delay = 100,
            multiplier = 2,
            jitter = 50,
            maxDelay = 1000
    )
    public void charge(
            Long walletId, String orderNumber, String paymentKey, Long amount
    ) {
        walletFacade.charge(walletId, orderNumber, paymentKey, amount);
    }

    // 낙관적 락 충돌로 롤백된 출금 처리를 최대 3회 재시도한다.
    @Retryable(
            value = {
                    OptimisticLockingFailureException.class,
                    CannotAcquireLockException.class
            },
            maxRetries = 3,
            delay = 100,
            multiplier = 2,
            jitter = 50,
            maxDelay = 1000
    )
    public void withdraw(
            Long walletId, Long withdrawOrderId
    ) {
        walletFacade.withdraw(walletId, withdrawOrderId);
    }

    // 동시 요청이 모두 기존 주문을 찾지 못해 생성 단계로 진입한 경우,
    // 유니크 제약으로 실패한 요청도 먼저 생성된 주문을 검증해 반환한다.
    public WalletChargeOrder chargeOrder(
            Long walletId, Long amount, String idempotencyKey
    ) {
        try {
            // 정상 처리되면 신규 또는 기존 주문을 바로 반환하고 종료한다.
            return walletFacade.chargeOrder(walletId, amount, idempotencyKey);
        } catch (DataIntegrityViolationException e){
            // 같은 키의 주문이 없으면 다른 DB 제약 오류이므로 원래 예외를 전달한다.
            if (!walletChargeOrderRepository.existsByIdempotencyKey(idempotencyKey)) {
                throw e;
            }
        }

        // 같은 키의 주문이 있으면 새 트랜잭션에서 지갑·금액을 검증하고 기존 주문을 반환한다.
        return walletFacade.chargeOrder(walletId, amount, idempotencyKey);
    }

    // 동시 요청이 모두 기존 주문을 찾지 못해 생성 단계로 진입한 경우,
    // 유니크 제약으로 실패한 요청도 먼저 생성된 주문을 검증해 반환한다.
    public WalletWithdrawOrder withdrawOrder(
            Long walletId, Long amount, String idempotencyKey
    ) {
        try {
            // 정상 처리되면 신규 또는 기존 주문을 바로 반환하고 종료한다.
            return walletFacade.withdrawOrder(walletId, amount, idempotencyKey);
        } catch (DataIntegrityViolationException e){
            // 같은 키의 주문이 없으면 다른 DB 제약 오류이므로 원래 예외를 전달한다.
            if (!walletWithdrawOrderRepository.existsByIdempotencyKey(idempotencyKey)) {
                throw e;
            }
        }

        // 같은 키의 주문이 있으면 새 트랜잭션에서 지갑·금액을 검증하고 기존 주문을 반환한다.
        return walletFacade.withdrawOrder(walletId, amount, idempotencyKey);
    }
}
