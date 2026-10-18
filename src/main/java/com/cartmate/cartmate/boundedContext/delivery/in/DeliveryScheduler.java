package com.cartmate.cartmate.boundedContext.delivery.in;

import com.cartmate.cartmate.boundedContext.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// 택배사 연동 전까지 출발 후 일정 시간이 지나면 배송완료로 바꾼다. 택배사 연동 시 이 처리를 대체한다.
@Slf4j
@Component
public class DeliveryScheduler {

    private static final long INTERVAL_MILLIS = 10_000L;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryFacade deliveryFacade;
    private final long delaySeconds;

    public DeliveryScheduler(
            DeliveryRepository deliveryRepository,
            DeliveryFacade deliveryFacade,
            @Value("${custom.delivery.autoComplete.delaySeconds:300}") long delaySeconds
    ) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryFacade = deliveryFacade;
        this.delaySeconds = delaySeconds;
    }

    // 출발 후 지정한 시간이 지난 배송중 건을 배송완료로 바꾼다. 건마다 별도 트랜잭션이라 한 건이 실패해도 나머지는 처리한다.
    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    public void completeShipped() {
        List<Long> ids = deliveryRepository.findIdsByStatusAndShippedAtBefore(
                DeliveryStatus.SHIPPING, LocalDateTime.now().minusSeconds(delaySeconds));

        for (Long id : ids) {
            try {
                deliveryFacade.deliverDelivery(id);
            } catch (Exception e) {
                log.error("배송 자동 완료 실패: id={}", id, e);
            }
        }
    }
}
