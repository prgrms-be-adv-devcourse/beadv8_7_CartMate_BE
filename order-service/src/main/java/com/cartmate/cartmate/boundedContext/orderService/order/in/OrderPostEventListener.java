package com.cartmate.cartmate.boundedContext.orderService.order.in;

import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;

import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;

import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.post.event.SalesPostHiddenEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPostEventListener {

    private final OrderFacade orderFacade;
    private final SharedGroupOrderFacade sharedGroupOrderFacade;

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SalesPostHiddenEvent event) {
        List<Long> productIds = event.productIds();
        if (productIds == null || productIds.isEmpty()) {
            return;
        }

        for (Long orderId : orderFacade.findPendingIdsByProductIds(productIds)) {
            run(event, "결제 대기 주문", orderId, () -> orderFacade.cancelBySalesStopped(orderId));
        }

        for (Long orderId : orderFacade.findPaidIdsByProductIds(productIds)) {
            run(event, "결제 완료 주문", orderId,
                    () -> orderFacade.cancelSellerItemsBySalesStopped(orderId, event.sellerId()));
        }

        for (Long orderId : sharedGroupOrderFacade.findActiveIdsByProductIds(productIds)) {
            try {
                sharedGroupOrderFacade.cancelBySalesStopped(orderId);
            } catch (ServiceException wholeRefused) {
                run(event, "공동 주문(부분)", orderId,
                        () -> sharedGroupOrderFacade.cancelSellerItemsBySalesStopped(orderId, event.sellerId()));
            } catch (Exception e) {
                log.error("판매글 #{} 숨김: 공동 주문 #{} 처리 실패", event.postId(), orderId, e);
            }
        }
    }

    private void run(SalesPostHiddenEvent event, String label, Long orderId, Runnable task) {
        try {
            task.run();
        } catch (InternalServerUnavailableException e) {
            log.warn("판매글 #{} 숨김: {} #{} 일시 장애로 지금 취소 못 함 ({})", event.postId(), label, orderId, e.getMessage());
        } catch (ServiceException e) {
            if (e.getResultCode().contains(OrderErrorCode.ORDER_PAYMENT_RELEASE_PENDING.getCode())) {
                log.warn("판매글 #{} 숨김: {} #{} 남은 결제 정리가 끝나지 않아 지금 취소 못 함, 결제 기한 만료 때 정리",
                        event.postId(), label, orderId);
                return;
            }
            log.info("판매글 #{} 숨김: {} #{} 자동 취소 안 함 ({})", event.postId(), label, orderId, e.getMessage());
        } catch (Exception e) {
            log.error("판매글 #{} 숨김: {} #{} 처리 실패 (대사 스케줄러가 다시 시도)", event.postId(), label, orderId, e);
        }
    }
}