package com.cartmate.cartmate.support;

import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.MatchingOutJpaSliceBeans;
import com.cartmate.cartmate.global.global.QuerydslConfig;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * cart · order · 매칭 저장소 · QueryDSL 테스트의 공용 슬라이스 설정(ARCH-18). 이 어노테이션을 단 테스트는 JPA 컨텍스트 하나를 같이 쓴다.
 * 테스트마다 @Import가 달라 컨텍스트가 따로 뜨던 것을 합쳤다. 슬라이스에 빈이 더 필요하면 각 테스트가 아니라 아래 목록에 더한다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({QuerydslConfig.class, MatchingOutJpaSliceBeans.class})
public @interface CartMateJpaTest {
}
