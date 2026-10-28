package com.cartmate.cartmate.boundedContext.abuse.in;

import com.cartmate.cartmate.boundedContext.abuse.app.AbuseFacade;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AbuseOrderEventListener {

    private final AbuseFacade abuseFacade;

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
}
