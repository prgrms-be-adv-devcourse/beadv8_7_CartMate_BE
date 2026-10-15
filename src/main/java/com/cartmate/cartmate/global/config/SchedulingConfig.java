package com.cartmate.cartmate.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/*
 * 스케줄러(@Scheduled) 기능을 켠다. (팀 공통 설정)
 *
 * - @EnableScheduling이 어딘가에 하나 있어야 @Scheduled 메서드가 실행된다. 없으면 에러 없이 조용히 아무것도 실행되지 않는다.
 * - 애플리케이션 전체에 하나만 있으면 되므로 도메인 패키지가 아니라 global/config에 둔다.
 *   다른 팀원(정산 배치, 주문 결제 기한 취소 등)의 스케줄러도 이 설정 하나로 함께 동작한다.
 *
 * @ConditionalOnProperty: 설정값으로 스케줄러를 끌 수 있게 한다.
 * - cartmate.scheduling.enabled=false 이면 이 설정 클래스가 등록되지 않아 모든 @Scheduled가 멈춘다.
 * - matchIfMissing = true: 설정값이 없으면 켜진 것으로 본다. 평소에는 아무것도 적지 않아도 동작한다.
 * - 쓰는 곳: 테스트에서 스케줄러가 몰래 돌아 데이터를 바꾸는 걸 막고 싶을 때, 또는 여러 서버 중 한 대만 스케줄러를 돌리고 싶을 때.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "cartmate.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
