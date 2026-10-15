package com.cartmate.cartmate.boundedContext.wallet.app;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletRetryService {

    private final WalletFacade walletFacade;

    // 낙관적 락 충돌로 롤백된 충전 처리를 최대 3회 재시도한다.
    @Retryable(
            value = OptimisticLockingFailureException.class,
            maxRetries = 3,
            delay = 100
    )
    public void charge(
            Long walletId, String orderNumber
    ) {
        walletFacade.charge(walletId, orderNumber);
    }

}
