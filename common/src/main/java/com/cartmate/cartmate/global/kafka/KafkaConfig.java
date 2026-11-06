package com.cartmate.cartmate.global.kafka;

import com.cartmate.cartmate.global.inbox.ProcessedEvents;
import com.cartmate.cartmate.global.outbox.InProcessOutboxRelaySink;
import com.cartmate.cartmate.global.outbox.OutboxRelaySink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * Kafka 공용 설정. {@code cartmate.kafka.enabled=true}인 서비스에서만 켜진다(기본 꺼짐 → 브로커가 없어도 지금처럼 동작).
 * 켜는 서비스는 {@code spring-boot-starter-kafka} 의존성과 {@code spring.kafka.*}(브로커 주소 · String 직렬화 · group-id)를 직접 둔다.
 *
 * <ul>
 *   <li>보내기: outbox 릴레이의 싱크를 "in-process → Kafka" 복합 싱크로 바꾼다</li>
 *   <li>받기: 서비스가 선언한 {@link InboundEventBinding} 토픽을 구독해 Spring 이벤트로 바꾼다</li>
 *   <li>실패: 1초 간격으로 2번 더 시도하고, 그래도 안 되면 {@code <토픽>-dlt}로 보낸다. JSON이 깨진 메시지는 바로 DLT</li>
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "cartmate.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    @Bean
    public KafkaOutboxRelaySink kafkaOutboxRelaySink(KafkaTemplate<String, String> kafkaTemplate,
                                                     ObjectMapper objectMapper,
                                                     @Value("${cartmate.kafka.send-timeout:10s}") Duration sendTimeout) {
        return new KafkaOutboxRelaySink(kafkaTemplate, objectMapper, sendTimeout);
    }

    // 릴레이가 쓰는 싱크. in-process 싱크 · Kafka 싱크도 각각 빈이라 @Primary로 이것을 고른다
    @Bean
    @Primary
    public OutboxRelaySink compositeOutboxRelaySink(InProcessOutboxRelaySink inProcessOutboxRelaySink,
                                                    KafkaOutboxRelaySink kafkaOutboxRelaySink) {
        return new CompositeOutboxRelaySink(inProcessOutboxRelaySink, kafkaOutboxRelaySink);
    }

    // Boot가 리스너 컨테이너 팩토리에 이 에러 핸들러를 넣는다.
    // DLT로 보낼 때 로그를 남긴다 — 기본 동작은 조용히 보내서 운영에서 버려진 메시지를 알아챌 수 없다
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer toDeadLetter = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> {
            log.error("Kafka 메시지 처리 실패 → DLT: topic={}, partition={}, offset={}, key={}",
                    record.topic(), record.partition(), record.offset(), record.key(), exception);
            toDeadLetter.accept(record, exception);
        }, new FixedBackOff(1_000L, 2L));
        handler.addNotRetryableExceptions(JacksonException.class); // 다시 읽어도 같은 결과
        return handler;
    }

    @Bean
    @SuppressWarnings("unchecked")
    public KafkaInboundEventBridge kafkaInboundEventBridge(ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory,
                                                           ObjectProvider<InboundEventBinding> bindings,
                                                           EventEnvelopeReader envelopeReader,
                                                           ApplicationEventPublisher applicationEventPublisher,
                                                           PlatformTransactionManager transactionManager,
                                                           ProcessedEvents processedEvents) {
        return new KafkaInboundEventBridge(
                (ConcurrentKafkaListenerContainerFactory<String, String>) kafkaListenerContainerFactory,
                bindings.orderedStream().toList(),
                envelopeReader,
                applicationEventPublisher,
                new TransactionTemplate(transactionManager),
                processedEvents);
    }
}
