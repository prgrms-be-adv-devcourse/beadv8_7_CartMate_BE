package com.cartmate.cartmate.boundedContext.orderService.order.in.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositFailedEvent;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// wallet 결과는 Kafka 수신 다리가 processed_event 기록과 같은 트랜잭션에서 다시 발행한다.
// 커밋 전에 그 트랜잭션 안에서 반영해, 반영이 실패하면 processed_event도 되돌려 Kafka가 다시 전달(재시도 · DLT)하게 한다
// (커밋 뒤에 반영하면 실패해도 이미 처리됨으로 걸러져 지급 행이 REQUESTED로 남는다)
@Component
@RequiredArgsConstructor
public class PaybackResultListener {

    private final OrderFacade orderFacade;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(PaybackDepositedEvent event) {
        orderFacade.applyPaybackDeposited(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(PaybackDepositFailedEvent event) {
        orderFacade.applyPaybackDepositFailed(event);
    }
}
