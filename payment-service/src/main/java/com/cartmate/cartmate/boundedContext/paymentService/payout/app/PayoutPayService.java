package com.cartmate.cartmate.boundedContext.paymentService.payout.app;

import com.cartmate.cartmate.global.lock.DistributedLockExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 정산 한 건을 주문 키 분산락 안에서 지급한다(ADR 0002·0009). 순서: 락 키 조회 → 락 획득 → 지급 트랜잭션 → 커밋 → 락 해제.
// 이 클래스에는 트랜잭션을 걸지 않는다. 걸면 락이 트랜잭션 안쪽에서 풀려 커밋 전에 같은 주문의 다른 정산이 들어온다.
// 락을 못 얻으면 LOCK_ACQUISITION_FAILED를 던진다. 배치는 그 정산을 건너뛰고 다음 실행에서 다시 시도한다.
@Service
@RequiredArgsConstructor
public class PayoutPayService {

    private final PayoutFacade payoutFacade;
    private final DistributedLockExecutor lockExecutor;

    public void pay(Long payoutId) {
        lockExecutor.run(payoutFacade.getHoldingLockKey(payoutId), () -> payoutFacade.pay(payoutId));
    }
}
