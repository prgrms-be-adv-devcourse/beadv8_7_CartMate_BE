package com.cartmate.cartmate.boundedContext.abuse.in;

import com.cartmate.cartmate.boundedContext.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AbuseOrderEventListener {

    private static final String DEADLINE_EXPIRED = "DEADLINE_EXPIRED";

    private final AbuseFacade abuseFacade;

    @EventListener
    public void receive(SharedGroupOrderCanceledEvent event) {
        if (!DEADLINE_EXPIRED.equals(event.cancelReason()) || event.unpaidMemberIds() == null) {
            return;
        }
        event.unpaidMemberIds().forEach(memberId -> toCommand(event, memberId).ifPresent(abuseFacade::receive));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SharedGroupOrderCanceledEvent event) {
        if (event.unpaidMemberIds() == null) {
            return;
        }
        event.unpaidMemberIds().forEach(memberId -> {
            try {
                abuseFacade.handleSharedGroupOrderCanceled(event, memberId);
            } catch (Exception e) {
                log.error("공동 주문 취소 어뷰징 신호 처리 실패: sharedGroupOrderId={}, memberId={}",
                        event.sharedGroupOrderId(), memberId, e);
            }
        });
    }

    private Optional<AbuseSignalCommand> toCommand(SharedGroupOrderCanceledEvent event, Long memberId) {
        try {
            return Optional.of(new AbuseSignalCommand(memberId, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED,
                    String.valueOf(event.sharedGroupOrderId()), event.canceledAt()));
        } catch (ServiceException e) {
            log.warn("잘못된 어뷰징 신호라 접수하지 않음: sharedGroupOrderId={}, memberId={}",
                    event.sharedGroupOrderId(), memberId);
            return Optional.empty();
        }
    }
}
