package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.shared.order.SharedGroupOrderCancelReason;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AbuseOrderEventConsumer {


    private final AbuseFacade abuseFacade;
    private final AbuseJudgeRunner abuseJudgeRunner;

    @TransactionalEventListener(fallbackExecution = true)
    public void handle(OutboxMessageRelayed message) {
        if (message.event() instanceof SharedGroupOrderCanceledEvent event && isSignal(event)) {
            consume(event);
        }
    }

    private void consume(SharedGroupOrderCanceledEvent event) {
        List<AbuseSignalCommand> commands = event.unpaidMemberIds().stream()
                .map(memberId -> toCommand(event, memberId))
                .flatMap(Optional::stream)
                .toList();
        commands.forEach(abuseFacade::receive);
        List<Long> memberIds = commands.stream().map(AbuseSignalCommand::memberId).distinct().toList();
        for (Long memberId : memberIds) {
            if (abuseJudgeRunner.judge(memberId) == AbuseJudgeOutcome.TRANSIENT_FAILURE) {
                log.warn("일시 장애로 판정을 멈춤, 남은 회원은 재판정 워커가 처리: sharedGroupOrderId={}",
                        event.sharedGroupOrderId());
                return;
            }
        }
    }

    private boolean isSignal(SharedGroupOrderCanceledEvent event) {
        return event.cancelReason() == SharedGroupOrderCancelReason.DEADLINE_EXPIRED && event.unpaidMemberIds() != null;
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
