package com.cartmate.cartmate.boundedContext.delivery.in;

import com.cartmate.cartmate.boundedContext.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.shared.order.event.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DeliveryOrderEventListener {

    private final DeliveryFacade deliveryFacade;

    // 결제 완료 이벤트를 받아 판매자별 배송을 생성한다. 생성 실패 시 결제 반영도 함께 롤백된다.
    @EventListener
    public void handle(OrderPaidEvent event) {
        Map<Long, List<OrderPaidEvent.Item>> bySeller = groupBySeller(event.items(), OrderPaidEvent.Item::sellerId);
        bySeller.forEach((sellerId, items) -> deliveryFacade.createDelivery(new DeliveryCreateCommand(
                event.memberId(),
                sellerId,
                event.orderId(),
                event.orderNumber(),
                PurchaseType.INDIVIDUAL_GROUP,
                event.deliveryAddress().receiverName(),
                event.deliveryAddress().receiverPhone(),
                event.deliveryAddress().zipCode(),
                event.deliveryAddress().address(),
                nullToEmpty(event.deliveryAddress().addressDetail()),
                "ORDER-PAID-" + event.orderId() + "-" + sellerId,
                items.stream()
                        .map(item -> toItem(item.orderItemId(), item.productName(),
                                item.optionName(), item.unitPrice(), item.quantity()))
                        .toList()
        )));
    }

    // 결제된 주문을 구매자가 취소하면 배송도 취소한다. 판매자가 이미 확인한 배송이면 예외로 주문 취소까지 롤백된다.
    // 결제 전 취소(paid=false)는 만들어진 배송이 없으므로 무시한다. 환불보다 먼저 실행되어야 한다.
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener
    public void handle(OrderCanceledEvent event) {
        if (!event.paid()) {
            return;
        }
        deliveryFacade.cancelByOrder(event.orderId());
    }

    // 판매글 판매 중지: 결제 완료 주문의 한 판매자 항목 취소 → 그 판매자 배송 취소.
    // 발송됐으면 예외로 주문 처리까지 롤백된다 (클레임으로). 환불보다 먼저 실행되어야 한다.
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener
    public void handle(OrderSellerItemsCanceledEvent event) {
        deliveryFacade.cancelSellerBySalesStop(event.orderId(), PurchaseType.INDIVIDUAL_GROUP, event.sellerId());
    }

    // 판매글 판매 중지: 확정 공동 주문의 한 판매자 항목 취소 → 그 판매자 배송 취소
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener
    public void handle(SharedGroupOrderSellerItemsCanceledEvent event) {
        deliveryFacade.cancelSellerBySalesStop(event.sharedGroupOrderId(), PurchaseType.SHARED_GROUP, event.sellerId());
    }

    // 판매글 판매 중지로 공동 주문 전체 취소 → 배송 전부 취소. 하나라도 발송됐으면 예외로 공동 주문 취소까지 롤백된다.
    // 다른 사유(기한 만료·만든 사람 취소)는 결제 중이라 배송이 없으므로 건드리지 않는다.
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener
    public void handle(SharedGroupOrderCanceledEvent event) {
        if ("SALES_STOPPED".equals(event.cancelReason())) {
            deliveryFacade.cancelAllSharedBySalesStop(event.sharedGroupOrderId());
        }
    }

    // 공동물품 확정 이벤트를 받아 판매자별 배송을 생성한다. 생성 실패 시 마지막 결제도 함께 롤백된다.
    @EventListener
    public void handle(SharedGroupOrderConfirmedEvent event) {
        Map<Long, List<SharedGroupOrderConfirmedEvent.Item>> bySeller =
                groupBySeller(event.items(), SharedGroupOrderConfirmedEvent.Item::sellerId);
        bySeller.forEach((sellerId, items) -> deliveryFacade.createDelivery(new DeliveryCreateCommand(
                event.creatorMemberId(),
                sellerId,
                event.sharedGroupOrderId(),
                null,   // 공유 공동구매 주문에는 주문번호가 없다
                PurchaseType.SHARED_GROUP,
                event.teamDeliveryAddress().receiverName(),
                event.teamDeliveryAddress().receiverPhone(),
                event.teamDeliveryAddress().zipCode(),
                event.teamDeliveryAddress().address(),
                nullToEmpty(event.teamDeliveryAddress().addressDetail()),
                "SHARED-GROUP-ORDER-" + event.sharedGroupOrderId() + "-" + sellerId,
                items.stream()
                        .map(item -> toItem(item.orderItemId(), item.productName(),
                                item.optionName(), item.unitPrice(), item.quantity()))
                        .toList()
        )));
    }

    // 판매자별로 배송을 나눈다. 입력 순서를 유지한다.
    private <T> Map<Long, List<T>> groupBySeller(List<T> items, Function<T, Long> sellerId) {
        return items.stream().collect(Collectors.groupingBy(sellerId, LinkedHashMap::new, Collectors.toList()));
    }

    private DeliveryCreateCommand.Item toItem(Long orderItemId, String productName, String optionName,
                                              long unitPrice, int quantity) {
        List<DeliveryCreateCommand.Option> options = (optionName == null || optionName.isBlank())
                ? List.of()
                : List.of(new DeliveryCreateCommand.Option("옵션", optionName));
        return new DeliveryCreateCommand.Item(orderItemId, productName, unitPrice, (long) quantity, options);
    }

    // 상세주소는 주문에서 선택 입력이지만 배송은 null을 허용하지 않아 빈 문자열로 바꾼다.
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
