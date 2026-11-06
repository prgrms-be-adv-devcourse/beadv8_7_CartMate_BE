package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.inbox.ProcessedEvents;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 다른 서비스가 Kafka로 보낸 이벤트를 이 서비스 안의 Spring 이벤트로 바꿔 주는 다리.
 * {@link InboundEventBinding}으로 선언한 토픽만 구독한다. 선언이 없으면 아무것도 구독하지 않는다.
 *
 * <p>한 메시지 처리:
 * <ol>
 *   <li>값을 {@link EventEnvelope}로 읽고, payload를 바인딩한 이벤트 클래스로 되돌린다</li>
 *   <li>트랜잭션 하나에서 {@code processed_event}에 (kafka:토픽, eventId)를 먼저 기록한다. 이미 있으면 건너뛴다(재전달 · 재처리)</li>
 *   <li>같은 트랜잭션에서 Spring 이벤트로 발행한다. 커밋 뒤 받는 쪽의 AFTER_COMMIT 리스너가 돈다(서버가 나뉘기 전과 같은 시점)</li>
 * </ol>
 * 1 · 2에서 난 예외는 컨테이너의 에러 핸들러로 가서 몇 번 다시 시도한 뒤 DLT로 보낸다({@link KafkaConfig}).
 * 받는 쪽 리스너가 커밋 뒤 실패하는 것은 여기까지 오지 않는다 — 서버를 나누기 전과 같다(각 리스너가 로그 · 대사로 처리).
 *
 * <p>{@code @KafkaListener} 대신 컨테이너를 직접 만든다. 구독할 토픽이 서비스마다 다르고, 없을 수도 있어서다.
 */
@Slf4j
public class KafkaInboundEventBridge implements SmartLifecycle {

    private final ConcurrentKafkaListenerContainerFactory<String, String> containerFactory;
    private final Map<String, Class<?>> eventTypeByTopic;
    private final EventEnvelopeReader envelopeReader;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ProcessedEvents processedEvents;

    private volatile ConcurrentMessageListenerContainer<String, String> container;

    public KafkaInboundEventBridge(ConcurrentKafkaListenerContainerFactory<String, String> containerFactory,
                                   List<InboundEventBinding> bindings,
                                   EventEnvelopeReader envelopeReader,
                                   ApplicationEventPublisher applicationEventPublisher,
                                   TransactionTemplate transactionTemplate,
                                   ProcessedEvents processedEvents) {
        this.containerFactory = containerFactory;
        this.eventTypeByTopic = bindings.stream()
                .collect(Collectors.toMap(InboundEventBinding::topic, InboundEventBinding::eventType));
        this.envelopeReader = envelopeReader;
        this.applicationEventPublisher = applicationEventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.processedEvents = processedEvents;
    }

    void onMessage(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = envelopeReader.envelope(record.value());
        Object event = envelopeReader.payload(envelope, eventTypeByTopic.get(record.topic()));
        transactionTemplate.executeWithoutResult(status -> {
            if (!processedEvents.markFirst(consumerName(record.topic()), envelope.eventId())) {
                log.info("이미 처리한 이벤트 건너뜀: topic={}, eventId={}", record.topic(), envelope.eventId());
                return;
            }
            applicationEventPublisher.publishEvent(event);
        });
    }

    // processed_event의 받는 쪽 이름. 토픽마다 따로 센다
    static String consumerName(String topic) {
        return "kafka:" + topic;
    }

    @Override
    public void start() {
        if (eventTypeByTopic.isEmpty() || container != null) {
            return;
        }
        ConcurrentMessageListenerContainer<String, String> created =
                containerFactory.createContainer(eventTypeByTopic.keySet().toArray(String[]::new));
        created.setupMessageListener((MessageListener<String, String>) this::onMessage);
        created.start();
        container = created;
        log.info("Kafka 수신 시작: topics={}", eventTypeByTopic.keySet());
    }

    @Override
    public void stop() {
        ConcurrentMessageListenerContainer<String, String> current = container;
        if (current != null) {
            current.stop();
            container = null;
        }
    }

    @Override
    public boolean isRunning() {
        return container != null && container.isRunning();
    }
}
