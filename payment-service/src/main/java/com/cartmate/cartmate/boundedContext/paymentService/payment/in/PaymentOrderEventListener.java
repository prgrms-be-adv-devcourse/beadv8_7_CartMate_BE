package com.cartmate.cartmate.boundedContext.paymentService.payment.in;

import com.cartmate.cartmate.boundedContext.paymentService.payment.app.PaymentFacade;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderSellerItemsCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

// 결제 후 환불은 판매자 승인(배송 클레임)으로만 처리한다.
// 예외: 공동물품 구매 무산은 판매자 승인 대상이 아니라 시스템이 자동 환불한다.
// 예외: 판매자 확인 전(배송요청 상태)에 구매자가 주문을 취소하면 전액 자동 환불한다.
/**
 * 주문 서비스의 취소 이벤트를 받아 환불한다.
 *
 * <p>주문 서비스와 다른 앱이라 Kafka로 받는다 (PaymentServiceKafkaConfig). KafkaInboundEventBridge가 원래 이벤트를
 * processed_event 기록과 <b>같은 트랜잭션</b>에서 발행하므로 일부러 {@code @EventListener}다:
 * 환불이 실패하면 기록까지 롤백 → Kafka가 다시 보내고(재시도 후 DLT), 성공하면 환불과 기록이 함께 커밋된다.
 * 같은 메시지가 두 번 와도 processed_event와 RefundKey(DB 유니크)가 한 번만 환불되게 막는다.
 */
@Component
@RequiredArgsConstructor
public class PaymentOrderEventListener {
    private final PaymentFacade paymentFacade;

    // 결제 전 취소(paid=false)는 환불할 결제가 없다.
    // 주의: 서비스가 나뉘어 배송이 같은 트랜잭션에서 취소를 막을 수 없다. 발송된 주문의 취소는 주문 서비스가 취소 전에 막아야 한다.
    @EventListener
    public void handle(OrderCanceledEvent event) {
        if (!event.paid()) {
            return;
        }
        paymentFacade.refundByBuyerCancel(event.orderId(), event.memberId(), event.refundAmount());
    }

    @EventListener
    public void handle(SharedGroupOrderCanceledEvent event) {
        paymentFacade.refundCanceledSharedGroupOrder(event.sharedGroupOrderId(), event.cancelReason().name(),
                canceledSellerAmounts(event));
    }

    // 판매 중지·판매자 사유 취소: 개인 주문 판매자 항목 취소 → 그 금액만 환불 (배송 취소와 같은 트랜잭션)
    @EventListener
    public void handle(OrderSellerItemsCanceledEvent event) {
        paymentFacade.refundOrderSellerItems(event.orderId(), event.buyerMemberId(), event.sellerId(), event.amount(),
                event.cancelReason());
    }

    // 판매 중지·판매자 사유 취소: 확정 공동 주문 판매자 항목 취소 → 결제한 사람들에게 참여자 몫으로 나눠 환불
    @EventListener
    public void handle(SharedGroupOrderSellerItemsCanceledEvent event) {
        paymentFacade.refundSharedSellerItems(event.sharedGroupOrderId(), event.sellerId(), event.amount(),
                event.cancelReason());
    }

    // 전체 취소보다 먼저 취소된 판매자 항목 금액들. 나누는 데는 금액만 필요하다.
    private static List<Long> canceledSellerAmounts(SharedGroupOrderCanceledEvent event) {
        if (event.canceledSellerAmounts() == null) {
            return null;
        }
        return event.canceledSellerAmounts().stream()
                .map(SharedGroupOrderCanceledEvent.SellerAmount::amount)
                .toList();
    }
}