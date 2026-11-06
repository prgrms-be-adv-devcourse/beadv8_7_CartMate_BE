package com.cartmate.cartmate.support;

import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * cart · order · abuse · 매칭 통합 테스트의 공용 설정(ARCH-18). 이 어노테이션을 단 테스트는 모두 같은 Spring 컨텍스트 하나를 쓴다.
 *
 * <p>설정이 조금이라도 다른 테스트마다 Spring이 컨텍스트를 새로 띄워 캐시하고, 컨텍스트마다 커넥션 풀을 쥔다.
 * 그래서 Testcontainers MySQL 하나(max_connections 151)를 모든 테스트가 같이 쓰면 연결 한도를 넘는다(PR #82 CI).
 * 테스트마다 다른 설정이 필요하면 새 속성 · @Import · @MockitoBean을 더하지 말고, 공용 대역(예: {@code FakeOrderProductClient}의
 * 덮어쓰기)이나 {@link TestEventRecorder}를 쓴다.
 *
 * <ul>
 *   <li>스케줄러 끔: 캐시된 컨텍스트에서 스케줄러가 계속 돌며 다른 테스트 데이터를 건드리지 않게</li>
 *   <li>아웃박스 릴레이는 수동: {@link ManualOutboxRelayConfig}가 릴레이 빈을 만들고 자동 폴링은 1시간 뒤로 미룬다.
 *       테스트가 {@code outboxRelay.relay()}를 직접 부른다. 이벤트 기록기({@link TestEventRecorder})도 여기서 들어온다</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(properties = {"cartmate.scheduling.enabled=false", "custom.outbox.relay.initial-delay=PT1H"})
@ActiveProfiles("test")
@Import(ManualOutboxRelayConfig.class)
public @interface CartMateIntegrationTest {
}
