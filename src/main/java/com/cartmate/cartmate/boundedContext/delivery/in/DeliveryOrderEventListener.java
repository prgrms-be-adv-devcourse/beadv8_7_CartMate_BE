package com.cartmate.cartmate.boundedContext.delivery.in;

import com.cartmate.cartmate.boundedContext.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryCreateCommand;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
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
                PurchaseType.INDIVIDUAL_GROUP,
                event.deliveryAddress().receiverName(),
                event.deliveryAddress().receiverPhone(),
                event.deliveryAddress().zipCode(),
                event.deliveryAddress().address(),
                event.deliveryAddress().addressDetail(),
                "ORDER-PAID-" + event.orderId() + "-" + sellerId,
                items.stream()
                        .map(item -> toItem(item.orderItemId(), item.productName(),
                                item.optionName(), item.unitPrice(), item.quantity()))
                        .toList()
        )));
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
                PurchaseType.SHARED_GROUP,
                event.teamDeliveryAddress().receiverName(),
                event.teamDeliveryAddress().receiverPhone(),
                event.teamDeliveryAddress().zipCode(),
                event.teamDeliveryAddress().address(),
                event.teamDeliveryAddress().addressDetail(),
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
}
