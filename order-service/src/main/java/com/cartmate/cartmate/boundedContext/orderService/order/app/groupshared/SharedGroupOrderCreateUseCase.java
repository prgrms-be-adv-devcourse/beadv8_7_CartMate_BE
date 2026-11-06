package com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.orderService.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCreatedEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCreationFailedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class SharedGroupOrderCreateUseCase {

    private final SharedGroupOrderRepository sharedGroupOrderRepository;
    private final OrderProductClient orderProductClient;
    private final DomainEventPublisher eventPublisher;
    private final Duration creationWindow;

    SharedGroupOrderCreateUseCase(
            SharedGroupOrderRepository sharedGroupOrderRepository,
            OrderProductClient orderProductClient,
            DomainEventPublisher eventPublisher,
            @Value("${custom.shared-group-order.creation-window:PT8M}") Duration creationWindow) {
        this.sharedGroupOrderRepository = sharedGroupOrderRepository;
        this.orderProductClient = orderProductClient;
        this.eventPublisher = eventPublisher;
        this.creationWindow = creationWindow;
    }

    void createFromClosedPurchase(SharedGroupPurchaseClosedEvent event, LocalDateTime now) {
        SharedGroupOrder order = sharedGroupOrderRepository.findBySharedGroupPurchaseId(event.sharedGroupPurchaseId())
                .orElseGet(() -> create(event, now));
        eventPublisher.publish(new SharedGroupOrderCreatedEvent(
                order.getId(), order.getSharedGroupPurchaseId(), order.getCreatedAt()));
    }

    private SharedGroupOrder create(SharedGroupPurchaseClosedEvent event, LocalDateTime now) {
        if (now.isAfter(event.closedAt().plus(creationWindow))) {
            throw new ServiceException(OrderErrorCode.ORDER_SHARED_CREATION_EXPIRED);
        }
        return sharedGroupOrderRepository.save(SharedGroupOrder.create(
                event.sharedGroupPurchaseId(),
                event.creatorMemberId(),
                event.participantMemberIds(),
                toItemLines(event.items()),
                event.closedAt()));
    }

    void reportCreationFailed(Long sharedGroupPurchaseId, String reason) {
        eventPublisher.publish(new SharedGroupOrderCreationFailedEvent(sharedGroupPurchaseId, reason));
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
