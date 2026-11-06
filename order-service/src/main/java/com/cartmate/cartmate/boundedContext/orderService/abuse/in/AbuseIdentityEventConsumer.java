package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseIdentityCommand;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.order.dto.DeliveryAddressInfo;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AbuseIdentityEventConsumer {

    private final AbuseFacade abuseFacade;

    @TransactionalEventListener(fallbackExecution = true, condition = "@relayedEventRouting.accepts(#message)")
    public void handle(OutboxMessageRelayed message) {
        switch (message.event()) {
            case OrderPaidEvent event -> accept(event);
            case SharedGroupOrderConfirmedEvent event -> accept(event);
            default -> {
            }
        }
    }

    public void accept(OrderPaidEvent event) {
        DeliveryAddressInfo address = event.deliveryAddress();
        if (event.memberId() != null && address != null) {
            abuseFacade.recordIdentity(new AbuseIdentityCommand(event.memberId(), address.receiverPhone(),
                    address.zipCode(), address.address(), address.addressDetail()));
        }
    }

    public void accept(SharedGroupOrderConfirmedEvent event) {
        TeamDeliveryAddress address = event.teamDeliveryAddress();
        if (event.creatorMemberId() != null && address != null) {
            abuseFacade.recordIdentity(new AbuseIdentityCommand(event.creatorMemberId(), address.receiverPhone(),
                    address.zipCode(), address.address(), address.addressDetail()));
        }
    }
}
