package com.cartmate.cartmate.boundedContext.paymentService.payout.in;

import com.cartmate.cartmate.boundedContext.paymentService.payout.app.PayoutFacade;
import com.cartmate.cartmate.boundedContext.etcService.shared.delivery.event.DeliveryConfirmedEvent;
import com.cartmate.cartmate.boundedContext.memberService.shared.member.event.SellerRegisteredEvent;
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

    // 판매자 등록 이벤트를 받아 지급에 쓸 판매자 정보를 복제한다.
    @EventListener
    public void handle(SellerRegisteredEvent event) {
        payoutFacade.registerSeller(event);
    }
}
