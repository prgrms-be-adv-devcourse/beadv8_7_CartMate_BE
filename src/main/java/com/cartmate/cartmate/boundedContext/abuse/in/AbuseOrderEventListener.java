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

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AbuseOrderEventListener {

    private static final String DEADLINE_EXPIRED = "DEADLINE_EXPIRED";

    private final AbuseFacade abuseFacade;

    @EventListener
    public void receive(SharedGroupOrderCanceledEvent event) {
        if (!isSignal(event)) {
            return;
        }
        event.unpaidMemberIds().forEach(memberId -> toCommand(event, memberId).ifPresent(abuseFacade::receive));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void judge(SharedGroupOrderCanceledEvent event) {
        if (!isSignal(event)) {
            return;
        }
        event.unpaidMemberIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(memberId -> judgeQuietly(event, memberId));
    }

    private boolean isSignal(SharedGroupOrderCanceledEvent event) {
        return DEADLINE_EXPIRED.equals(event.cancelReason()) && event.unpaidMemberIds() != null;
    }

    private void judgeQuietly(SharedGroupOrderCanceledEvent event, Long memberId) {
        try {
            abuseFacade.judgePending(memberId);
        } catch (Exception e) {
            log.error("어뷰징 판정 실패, 재판정 대기: sharedGroupOrderId={}, memberId={}",
                    event.sharedGroupOrderId(), memberId, e);
        }
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
