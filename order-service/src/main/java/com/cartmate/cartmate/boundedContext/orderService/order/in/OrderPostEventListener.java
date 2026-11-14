package com.cartmate.cartmate.boundedContext.orderService.order.in;

import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;

import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;

import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.app.salesstop.SalesStopCancelFacade;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;
import com.cartmate.cartmate.shared.post.event.SalesPostHiddenEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPostEventListener {

    private final OrderFacade orderFacade;
    private final SharedGroupOrderFacade sharedGroupOrderFacade;
    private final SalesStopCancelFacade salesStopCancelFacade;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(SalesPostHiddenEvent event) {
        List<Long> productIds = event.productIds();
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        List<RuntimeException> failures = new ArrayList<>();

        for (Long orderId : orderFacade.findPendingIdsByProductIds(productIds)) {
            run(event, "결제 대기 주문", orderId, () -> orderFacade.cancelBySalesStopped(orderId), failures);
        }

        for (Long orderId : orderFacade.findPaidIdsByProductIds(productIds)) {
            run(event, "결제 완료 주문", orderId,
                    () -> salesStopCancelFacade.request(PurchaseType.INDIVIDUAL_GROUP, orderId, event.sellerId()), failures);
        }

        for (Long orderId : sharedGroupOrderFacade.findIdsByStatusAndProductIds(SharedGroupOrderStatus.PAYING, productIds)) {
            run(event, "결제 중 공동 주문", orderId, () -> sharedGroupOrderFacade.cancelBySalesStopped(orderId), failures);
        }

        for (Long orderId : sharedGroupOrderFacade.findIdsByStatusAndProductIds(SharedGroupOrderStatus.CONFIRMED, productIds)) {
            run(event, "확정 공동 주문", orderId,
                    () -> salesStopCancelFacade.request(PurchaseType.SHARED_GROUP, orderId, event.sellerId()), failures);
        }

        if (!failures.isEmpty()) {
            throw failures.getFirst();
        }
    }

    private void run(SalesPostHiddenEvent event, String label, Long orderId, Runnable task,
                     List<RuntimeException> failures) {
        try {
            task.run();
        } catch (InternalServerUnavailableException e) {
            log.warn("판매글 #{} 숨김: {} #{} 일시 장애, 인박스가 다시 처리 ({})", event.postId(), label, orderId, e.getMessage());
            failures.add(e);
        } catch (ServiceException e) {
            if (e.getResultCode().contains(OrderErrorCode.ORDER_PAYMENT_RELEASE_PENDING.getCode())) {
                log.warn("판매글 #{} 숨김: {} #{} 남은 결제 정리가 끝나지 않아 지금 취소 못 함, 결제 기한 만료 때 정리",
                        event.postId(), label, orderId);
                return;
            }
            log.info("판매글 #{} 숨김: {} #{} 자동 취소 안 함 ({})", event.postId(), label, orderId, e.getMessage());
        } catch (RuntimeException e) {
            log.error("판매글 #{} 숨김: {} #{} 처리 실패, 인박스가 다시 처리", event.postId(), label, orderId, e);
            failures.add(e);
        }
    }
}
