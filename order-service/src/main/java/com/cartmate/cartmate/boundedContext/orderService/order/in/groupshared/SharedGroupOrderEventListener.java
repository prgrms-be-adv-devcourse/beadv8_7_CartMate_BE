package com.cartmate.cartmate.boundedContext.orderService.order.in.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SharedGroupOrderEventListener {

    private final SharedGroupOrderFacade sharedGroupOrderFacade;

    @TransactionalEventListener(fallbackExecution = true, condition = "@relayedEventRouting.accepts(#message)")
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
