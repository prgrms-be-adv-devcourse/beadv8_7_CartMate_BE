package com.cartmate.cartmate.global.kafka;

/**
 * 다른 서비스가 Kafka로 보내는 이벤트 중 이 서비스가 받을 것. 서비스마다 {@code @Bean}으로 선언한다.
 * {@link KafkaInboundEventBridge}가 이 토픽들을 구독해, 봉투의 payload를 {@code eventType} 클래스로 되돌린 뒤
 * 같은 서비스 안에 Spring 이벤트로 발행한다. 그래서 받는 쪽 리스너(@TransactionalEventListener)는 고치지 않아도 된다.
 *
 * @param topic     구독할 토픽 ({@code cartmate.<모듈>.<이벤트-kebab>})
 * @param eventType 봉투 payload를 되돌릴 이벤트 클래스(shared 아래 계약)
 */
public record InboundEventBinding(String topic, Class<?> eventType) {
}
