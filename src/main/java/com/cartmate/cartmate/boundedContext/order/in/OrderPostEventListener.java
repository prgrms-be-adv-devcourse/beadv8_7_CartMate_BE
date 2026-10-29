package com.cartmate.cartmate.boundedContext.order.in;

import com.cartmate.cartmate.boundedContext.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.order.app.personal.OrderFacade;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.post.event.SalesPostHiddenEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 판매글이 내려가면 진행 중 주문을 정리한다. 주문이 취소를 지휘하고, delivery·payment는 order의 이벤트에 반응한다.
 *
 * <p>판매글 숨김이 커밋된 뒤 별도 스레드에서 실행되고(@Async), 주문 하나마다 별도 트랜잭션이다.
 * 발송 여부는 order가 몰라도 된다. delivery가 발송된 배송의 취소를 거절하면 그 주문의 처리 전체가 롤백된다.
 * 모든 처리는 멱등이라 대사 스케줄러가 같은 이벤트를 다시 보내도 안전하다.
 */
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

        // 개인 주문 결제 대기 → 주문 전체 취소 (돈이 안 나갔으니 다시 주문하면 됨)
        for (Long orderId : orderFacade.findPendingIdsByProductIds(productIds)) {
            run(event, "결제 대기 주문", orderId, () -> orderFacade.cancelBySalesStopped(orderId));
        }

        // 개인 주문 결제 완료 → 숨긴 판매자 상품만 취소·환불 (발송됐으면 delivery가 거절 → 클레임으로)
        for (Long orderId : orderFacade.findPaidIdsByProductIds(productIds)) {
            run(event, "결제 완료 주문", orderId,
                    () -> orderFacade.cancelSellerItemsBySalesStopped(orderId, event.sellerId(), event.postId()));
        }

        // 공동 주문 (결제 중·확정) → 전체 취소 시도, 다른 판매자 배송이 이미 발송돼 거절되면 숨긴 판매자 몫만
        for (Long orderId : sharedGroupOrderFacade.findActiveIdsByProductIds(productIds)) {
            try {
                sharedGroupOrderFacade.cancelBySalesStopped(orderId);
            } catch (ServiceException wholeRefused) {
                run(event, "공동 주문(부분)", orderId,
                        () -> sharedGroupOrderFacade.cancelSellerItemsBySalesStopped(orderId, event.sellerId(), event.postId()));
            } catch (Exception e) {
                log.error("판매글 #{} 숨김: 공동 주문 #{} 처리 실패", event.postId(), orderId, e);
            }
        }
    }

    private void run(SalesPostHiddenEvent event, String label, Long orderId, Runnable task) {
        try {
            task.run();
        } catch (ServiceException e) {
            // 발송된 배송 취소 거절 등 정책상 처리하지 않는 경우 → 클레임으로
            log.info("판매글 #{} 숨김: {} #{} 자동 취소 안 함 ({})", event.postId(), label, orderId, e.getMessage());
        } catch (Exception e) {
            log.error("판매글 #{} 숨김: {} #{} 처리 실패 (대사 스케줄러가 다시 시도)", event.postId(), label, orderId, e);
        }
    }
}