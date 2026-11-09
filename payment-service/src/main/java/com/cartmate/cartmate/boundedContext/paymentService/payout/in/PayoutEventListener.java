package com.cartmate.cartmate.boundedContext.paymentService.payout.in;

import com.cartmate.cartmate.boundedContext.paymentService.payout.app.PayoutFacade;
import com.cartmate.cartmate.shared.member.event.SellerRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PayoutEventListener {

    private final PayoutFacade payoutFacade;

    // 판매자 등록 이벤트를 받아 지급에 쓸 판매자 정보를 복제한다.
    @EventListener
    public void handle(SellerRegisteredEvent event) {
        payoutFacade.registerSeller(event);
    }
}
