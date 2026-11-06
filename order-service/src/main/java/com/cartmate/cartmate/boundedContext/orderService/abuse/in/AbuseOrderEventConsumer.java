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

/**
 * 공동 주문 이벤트로 어뷰징 신호를 접수하고 판정한다. 진입점이 둘이고 처리({@link #accept})는 하나다(MSA-9).
 * <ul>
 *   <li>Kafka가 꺼져 있으면(로컬 · 테스트 기본): 아웃박스 릴레이가 같은 프로세스에 전달한 것을 {@link #handle}로 받는다</li>
 *   <li>Kafka가 켜져 있으면: {@link AbuseOrderKafkaConsumer}가 토픽에서 받아 {@link #accept}를 부르고, {@link #handle}은 돌지 않는다
 *       (복합 싱크가 in-process로도 보내므로 둘 다 돌면 같은 신호를 두 번 처리하려 든다 — unique로 막히긴 하지만 판정을 두 번 돌린다)</li>
 * </ul>
 * Kafka 필수로 바꾸면(MSA-11) {@link #handle}과 이 조건을 없앤다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbuseOrderEventConsumer {

    private final AbuseFacade abuseFacade;
    private final AbuseJudgeRunner abuseJudgeRunner;

    @TransactionalEventListener(fallbackExecution = true,
            condition = "@environment.getProperty('cartmate.kafka.enabled', 'false') != 'true'")
    public void handle(OutboxMessageRelayed message) {
        if (message.event() instanceof SharedGroupOrderCanceledEvent event) {
            accept(event);
        }
    }

    // 두 진입점이 같이 쓰는 처리. 접수 예외는 그대로 던져 재전달되게 한다(릴레이 재시도 · Kafka 재시도 → DLT)
    public void accept(SharedGroupOrderCanceledEvent event) {
        if (isSignal(event)) {
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
