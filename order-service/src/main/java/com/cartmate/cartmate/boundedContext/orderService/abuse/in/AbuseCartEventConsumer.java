package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.cart.SharedGroupPurchaseCancelReason;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseCanceledEvent;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberJoinedEvent;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberLeftEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AbuseCartEventConsumer {

    static final long CONCURRENT_JOIN_THRESHOLD = 5;

    private final AbuseSignalIntake abuseSignalIntake;

    @TransactionalEventListener(fallbackExecution = true, condition = "@relayedEventRouting.accepts(#message)")
    public void handle(OutboxMessageRelayed message) {
        switch (message.event()) {
            case SharedGroupPurchaseMemberJoinedEvent event -> accept(event);
            case SharedGroupPurchaseMemberLeftEvent event -> accept(event);
            case SharedGroupPurchaseCanceledEvent event -> accept(event);
            default -> {
            }
        }
    }

    public void accept(SharedGroupPurchaseMemberJoinedEvent event) {
        if (event.activeParticipationCount() >= CONCURRENT_JOIN_THRESHOLD) {
            receiveOne(AbuseSignalIntake.command(event.memberId(), AbuseSignalType.SHARED_PURCHASE_CONCURRENT_JOIN,
                    event.sharedGroupPurchaseId() + ":" + event.joinedAt(), event.joinedAt(), event.sharedGroupPurchaseId()),
                    event.sharedGroupPurchaseId());
        }
    }

    public void accept(SharedGroupPurchaseMemberLeftEvent event) {
        receiveOne(AbuseSignalIntake.command(event.memberId(), AbuseSignalType.SHARED_PURCHASE_LEFT,
                event.sharedGroupPurchaseId() + ":" + event.leftAt(), event.leftAt(), event.sharedGroupPurchaseId()),
                event.sharedGroupPurchaseId());
    }

    public void accept(SharedGroupPurchaseCanceledEvent event) {
        if (event.cancelReason() == SharedGroupPurchaseCancelReason.CREATOR_CANCELED) {
            receiveOne(AbuseSignalIntake.command(event.creatorMemberId(), AbuseSignalType.SHARED_PURCHASE_CREATOR_CANCELED,
                    "purchase:" + event.sharedGroupPurchaseId(), event.canceledAt(), event.sharedGroupPurchaseId()),
                    event.sharedGroupPurchaseId());
        }
    }

    private void receiveOne(Optional<AbuseSignalCommand> command, Long sharedGroupPurchaseId) {
        command.ifPresent(c -> abuseSignalIntake.receiveAndJudge(List.of(c), "sharedGroupPurchase:" + sharedGroupPurchaseId));
    }
}
