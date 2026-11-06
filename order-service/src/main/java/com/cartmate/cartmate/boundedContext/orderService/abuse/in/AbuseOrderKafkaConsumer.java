package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.kafka.EventEnvelopeReader;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 어뷰징 신호의 Kafka 진입점(MSA-9). Kafka가 켜져 있을 때만 만들어지고, 그때는 in-process 진입점이 쉰다.
 *
 * <ul>
 *   <li>토픽: 공동 주문 취소 {@code cartmate.order.shared-group-order-canceled}. 문자열로 적지 않고 발행 쪽과 같은 규칙
 *       ({@link OutboxTopics#of})으로 계산해 어긋나지 않게 한다. 키 = 공동 주문 ID라 같은 주문의 이벤트는 순서대로 온다</li>
 *   <li>그룹: {@value #GROUP_ID} — 모듈별 그룹이라, 나중에 abuse를 따로 떼어 내도 읽은 위치(오프셋)를 그대로 가져간다</li>
 *   <li>중복: 같은 메시지가 다시 와도 신호는 abuse_signal unique로 1건, 판정은 이미 판정한 신호를 건너뛴다</li>
 *   <li>실패: 접수 예외는 공용 에러 핸들러가 2번 더 시도한 뒤 DLT로 보낸다. 판정 실패는 던지지 않고 재판정 워커가 맡는다</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
public class AbuseOrderKafkaConsumer {

    static final String TOPIC = "#{T(com.cartmate.cartmate.global.outbox.OutboxTopics)"
            + ".of(T(com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent))}";
    static final String GROUP_ID = "order-service-abuse";

    private final EventEnvelopeReader envelopeReader;
    private final AbuseOrderEventConsumer abuseOrderEventConsumer;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void onMessage(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = envelopeReader.envelope(record.value());
        SharedGroupOrderCanceledEvent event = envelopeReader.payload(envelope, SharedGroupOrderCanceledEvent.class);
        log.debug("어뷰징 신호 수신: eventId={}, sharedGroupOrderId={}", envelope.eventId(), event.sharedGroupOrderId());
        abuseOrderEventConsumer.accept(event);
    }
}
