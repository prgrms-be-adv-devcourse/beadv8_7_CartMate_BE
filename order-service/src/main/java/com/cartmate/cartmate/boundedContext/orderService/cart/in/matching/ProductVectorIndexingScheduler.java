package com.cartmate.cartmate.boundedContext.orderService.cart.in.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.matching.CartMatchingFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${cartmate.scheduling.enabled:true} and ${custom.matching.indexing.enabled:false}")
public class ProductVectorIndexingScheduler {

    static final String LOCK_NAME = "cart.indexProductVectors";
    private static final long INTERVAL_MILLIS = 60_000L;

    private final CartMatchingFacade cartMatchingFacade;

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void indexCartProductVectors() {
        try {
            int indexed = cartMatchingFacade.indexCartProductVectors();
            if (indexed > 0) {
                log.info("Indexed {} cart product vectors", indexed);
            }
        } catch (RuntimeException e) {
            log.warn("Cart product vector indexing failed; retrying on the next run", e);
        }
    }
}
