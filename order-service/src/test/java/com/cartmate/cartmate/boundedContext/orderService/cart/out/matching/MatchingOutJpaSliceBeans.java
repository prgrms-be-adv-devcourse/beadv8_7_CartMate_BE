package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

// CartDemandQueryDslReader가 패키지 전용이라 공용 JPA 슬라이스(CartMateJpaTest)가 직접 가져올 수 없어 이 패키지에서 대신 넘긴다
@TestConfiguration
@Import(CartDemandQueryDslReader.class)
public class MatchingOutJpaSliceBeans {
}
