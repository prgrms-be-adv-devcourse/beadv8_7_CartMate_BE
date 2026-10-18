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
import java.util.function.Consumer;

@Slf4j
@Component
public class DeliveryScheduler {

    private static final long COMPLETE_INTERVAL_MILLIS = 10_000L;
    private static final long CONFIRM_INTERVAL_MILLIS = 600_000L;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryFacade deliveryFacade;
    private final long completeDelaySeconds;
    private final long confirmDelayDays;

    public DeliveryScheduler(
            DeliveryRepository deliveryRepository,
            DeliveryFacade deliveryFacade,
            @Value("${custom.delivery.autoComplete.delaySeconds:300}") long completeDelaySeconds,
            @Value("${custom.delivery.autoConfirm.delayDays:7}") long confirmDelayDays
    ) {
        this.deliveryRepository = deliveryRepository;
        this.deliveryFacade = deliveryFacade;
        this.completeDelaySeconds = completeDelaySeconds;
        this.confirmDelayDays = confirmDelayDays;
    }

    // 택배사 연동 전까지 출발 후 일정 시간이 지난 배송중 건을 배송완료로 바꾼다. 택배사 연동 시 이 처리를 대체한다.
    @Scheduled(fixedDelay = COMPLETE_INTERVAL_MILLIS, initialDelay = COMPLETE_INTERVAL_MILLIS)
    public void completeShipped() {
        List<Long> ids = deliveryRepository.findIdsByStatusAndShippedAtBefore(
                DeliveryStatus.SHIPPING, LocalDateTime.now().minusSeconds(completeDelaySeconds));
        processEach(ids, deliveryFacade::deliverDelivery, "자동 배송완료");
    }

    // 배송완료 후 지정한 기간(기본 7일)이 지나도 구매자가 확정하지 않은 건을 구매확정으로 바꾼다.
    @Scheduled(fixedDelay = CONFIRM_INTERVAL_MILLIS, initialDelay = CONFIRM_INTERVAL_MILLIS)
    public void confirmDelivered() {
        List<Long> ids = deliveryRepository.findIdsByStatusAndDeliveredAtBefore(
                DeliveryStatus.DELIVERED, LocalDateTime.now().minusDays(confirmDelayDays));
        processEach(ids, deliveryFacade::autoConfirmDelivery, "자동 구매확정");
    }

    // 건마다 별도 트랜잭션이라 한 건이 실패해도 나머지는 계속 처리한다.
    private void processEach(List<Long> ids, Consumer<Long> action, String taskName) {
        for (Long id : ids) {
            try {
                action.accept(id);
            } catch (Exception e) {
                log.error("배송 {} 실패: id={}", taskName, id, e);
            }
        }
    }
}
