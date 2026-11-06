package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.order.OrderCancelReason;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.shared.order.SharedGroupOrderCancelReason;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AbuseOrderEventConsumer {

    private final AbuseSignalIntake abuseSignalIntake;

    @TransactionalEventListener(fallbackExecution = true, condition = "@relayedEventRouting.accepts(#message)")
    public void handle(OutboxMessageRelayed message) {
        if (message.event() instanceof SharedGroupOrderCanceledEvent event) {
            accept(event);
        } else if (message.event() instanceof OrderCanceledEvent event) {
            accept(event);
        }
    }

    public void accept(SharedGroupOrderCanceledEvent event) {
        if (event.cancelReason() == SharedGroupOrderCancelReason.DEADLINE_EXPIRED && event.unpaidMemberIds() != null) {
            List<AbuseSignalCommand> commands = event.unpaidMemberIds().stream()
                    .map(memberId -> AbuseSignalIntake.command(memberId, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                            String.valueOf(event.sharedGroupOrderId()), event.canceledAt()))
                    .flatMap(Optional::stream)
                    .toList();
            abuseSignalIntake.receiveAndJudge(commands, "sharedGroupOrder:" + event.sharedGroupOrderId());
        } else if (event.cancelReason() == SharedGroupOrderCancelReason.CREATOR_CANCELED
                && event.creatorMemberId() != null) {
            receiveOne(AbuseSignalIntake.command(event.creatorMemberId(),
                    AbuseSignalType.SHARED_PURCHASE_CREATOR_CANCELED,
                    "order:" + event.sharedGroupOrderId(), event.canceledAt()),
                    "sharedGroupOrder:" + event.sharedGroupOrderId());
        }
    }

    public void accept(OrderCanceledEvent event) {
        if (event.orderType() == OrderType.INDIVIDUAL_GROUP && event.cancelReason() == OrderCancelReason.PAYMENT_EXPIRED) {
            receiveOne(AbuseSignalIntake.command(event.memberId(), AbuseSignalType.INDIVIDUAL_ORDER_PAYMENT_EXPIRED,
                    String.valueOf(event.orderId()), event.canceledAt()), "order:" + event.orderId());
        }
    }

    private void receiveOne(Optional<AbuseSignalCommand> command, String source) {
        command.ifPresent(c -> abuseSignalIntake.receiveAndJudge(List.of(c), source));
    }
}
