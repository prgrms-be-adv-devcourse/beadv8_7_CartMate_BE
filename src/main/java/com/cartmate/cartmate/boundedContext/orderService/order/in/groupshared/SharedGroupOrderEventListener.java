package com.cartmate.cartmate.boundedContext.orderService.order.in.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// 공동구매 마감은 cart 트랜잭션에서 CLOSED + 아웃박스 저장까지만 한다. 공동 주문은 릴레이가 넘긴 마감 이벤트를 받아
// order 자기 트랜잭션에서 만든다. 릴레이는 트랜잭션 밖에서 발행하니 fallbackExecution으로 바로 실행된다.
// - 업무 규칙 실패(ServiceException, 예: 상품을 찾을 수 없음)는 다시 해도 같으므로 실패 소식을 내고 이 전달은 끝낸다
// - 그 밖의 예외(DB 순단 등)는 그대로 릴레이로 올려 백오프 재시도한다
// 멱등: 공동구매당 공동 주문 하나(unique). 중복 전달은 만들지 않고 생성 소식만 다시 낸다
@Slf4j
@Component
@RequiredArgsConstructor
public class SharedGroupOrderEventListener {

    private final SharedGroupOrderFacade sharedGroupOrderFacade;

    @TransactionalEventListener(fallbackExecution = true)
    public void handle(OutboxMessageRelayed message) {
        if (!(message.event() instanceof SharedGroupPurchaseClosedEvent event)) {
            return;
        }
        try {
            sharedGroupOrderFacade.createFromClosedPurchase(event);
        } catch (ServiceException e) {
            log.warn("공동 주문 생성 실패, 공동구매를 마감 보류로 돌림: sharedGroupPurchaseId={} eventId={} reason={}",
                    event.sharedGroupPurchaseId(), message.envelope().eventId(), e.getResultCode());
            sharedGroupOrderFacade.reportCreationFailed(event.sharedGroupPurchaseId(), e.getResultCode());
        }
    }
}
