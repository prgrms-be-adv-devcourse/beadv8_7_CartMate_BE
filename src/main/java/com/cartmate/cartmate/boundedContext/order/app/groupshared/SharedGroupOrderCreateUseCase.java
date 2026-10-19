package com.cartmate.cartmate.boundedContext.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class SharedGroupOrderCreateUseCase {

    private final SharedGroupOrderRepository sharedGroupOrderRepository;
    private final OrderProductClient orderProductClient;

    void createFromClosedPurchase(SharedGroupPurchaseClosedEvent event) {
        if (sharedGroupOrderRepository.existsBySharedGroupPurchaseId(event.sharedGroupPurchaseId())) {
            return;
        }
        SharedGroupOrder order = SharedGroupOrder.create(
                event.sharedGroupPurchaseId(),
                event.creatorMemberId(),
                event.participantMemberIds(),
                toItemLines(event.items()),
                event.closedAt());
        sharedGroupOrderRepository.save(order);
    }

    private List<OrderItemLine> toItemLines(List<SharedGroupPurchaseClosedEvent.Item> items) {
        List<Long> inventoryIds = items.stream()
                .map(SharedGroupPurchaseClosedEvent.Item::inventoryId)
                .toList();
        Map<Long, OrderProductInfo> products = orderProductClient.findAllByInventoryIds(inventoryIds).stream()
                .collect(Collectors.toMap(OrderProductInfo::inventoryId, Function.identity()));
        return items.stream()
                .map(item -> toItemLine(item, productOf(products, item.inventoryId())))
                .toList();
    }

    private OrderItemLine toItemLine(SharedGroupPurchaseClosedEvent.Item item, OrderProductInfo product) {
        return new OrderItemLine(
                item.productId(),
                item.inventoryId(),
                product.sellerId(),
                product.productName(),
                product.optionName(),
                item.quantity(),
                product.price());
    }

    private OrderProductInfo productOf(Map<Long, OrderProductInfo> products, Long inventoryId) {
        OrderProductInfo product = products.get(inventoryId);
        if (product == null) {
            throw new ServiceException(OrderErrorCode.ORDER_PRODUCT_NOT_FOUND);
        }
        return product;
    }
}
