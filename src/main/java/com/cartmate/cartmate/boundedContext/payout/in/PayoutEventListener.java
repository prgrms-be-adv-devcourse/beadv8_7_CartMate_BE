package com.cartmate.cartmate.boundedContext.payout.in;

import com.cartmate.cartmate.boundedContext.payout.app.PayoutFacade;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PayoutEventListener {

    private final PayoutFacade payoutFacade;

    // 구매확정 이벤트를 받아 정산을 생성한다. 생성 실패 시 구매확정도 함께 롤백된다.
    @EventListener
    public void handle(DeliveryConfirmedEvent event) {
        payoutFacade.createPayouts(event);
    }
}
