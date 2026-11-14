package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import({CartDemandQueryDslReader.class, JpaProductVectorIndex.class})
public class MatchingOutJpaSliceBeans {
}
