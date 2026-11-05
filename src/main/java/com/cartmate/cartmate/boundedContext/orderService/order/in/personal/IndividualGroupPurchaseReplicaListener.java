package com.cartmate.cartmate.boundedContext.orderService.order.in.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.boundedContext.orderService.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.boundedContext.orderService.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// 아웃박스 릴레이가 트랜잭션 밖에서 넘기는 cart 공동구매 이벤트로 order 쪽 공동구매 사본을 고친다.
// 트랜잭션이 없으니 fallbackExecution으로 바로 실행되고, 예외는 그대로 릴레이로 올라가 다시 시도된다.
// 멱등 · 순서: 사본이 cart의 revision을 비교해 늦게 온 · 중복 이벤트를 버린다.
// 종료: 결제 마감 = 판매 마감(ARCH-17). 사본을 닫아 커밋한 뒤 남은 결제 대기 주문을 하나씩 취소한다.
// 사본을 먼저 닫으므로 그 뒤의 결제는 거절되고, 취소는 결제 대기만 건드려 그 전에 끝난 결제는 결제 완료로 남는다.
// 중간에 실패하면 예외로 릴레이가 이 이벤트를 다시 보내고, 사본 닫기는 멱등 · 이미 취소한 주문은 건너뛴다
@Slf4j
@Component
@RequiredArgsConstructor
public class IndividualGroupPurchaseReplicaListener {

    private final OrderFacade orderFacade;

    @TransactionalEventListener(fallbackExecution = true)
    public void handle(OutboxMessageRelayed message) {
        switch (message.event()) {
            case IndividualGroupPurchaseOpenedEvent event -> orderFacade.applyIndividualGroupPurchaseOpened(event);
            case IndividualGroupPurchaseUpdatedEvent event -> orderFacade.applyIndividualGroupPurchaseUpdated(event);
            case IndividualGroupPurchaseClosedEvent event -> {
                orderFacade.applyIndividualGroupPurchaseClosed(event);
                cancelPendingOrders(event.individualGroupPurchaseId());
            }
            default -> {
            }
        }
    }

    // 하나가 실패해도 나머지는 계속 취소하고, 끝에 첫 예외를 던져 릴레이가 실패한 것만 다시 하게 한다
    private void cancelPendingOrders(Long individualGroupPurchaseId) {
        RuntimeException failure = null;
        for (Long orderId : orderFacade.findPendingIdsOfIndividualGroupPurchase(individualGroupPurchaseId)) {
            try {
                orderFacade.cancelByGroupPurchaseClosed(orderId);
            } catch (RuntimeException e) {
                log.warn("공동구매 종료 자동 취소 실패, 릴레이가 다시 시도: purchaseId={} orderId={}",
                        individualGroupPurchaseId, orderId, e);
                if (failure == null) {
                    failure = e;
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
