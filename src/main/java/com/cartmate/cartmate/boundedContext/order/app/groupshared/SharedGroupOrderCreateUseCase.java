package com.cartmate.cartmate.boundedContext.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.event.DomainEventPublisher;
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
    // 마감 뒤 이 시간이 지나면 새로 만들지 않는다. cart는 마감 뒤 10분(custom.shared-group-purchase.order-creation-timeout)
    // 안에 생성 소식이 없으면 FROZEN으로 돌리므로, 그보다 짧게 잡아 FROZEN으로 돌아간 뒤 늦게 주문이 생기는 일을 막는다
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

    // 공동구매 하나에 공동 주문 하나(shared_group_purchase_id unique). 이미 있으면 다시 만들지 않고 생성 소식만 다시 낸다:
    // 중복 전달이면 cart가 그냥 넘기고, 시간 초과로 FROZEN에 돌아갔다 재마감된 경우면 cart가 이 소식으로 ORDER_CREATED가 된다
    // 재시도가 길어져 생성 창을 넘긴 마감은 업무 규칙 실패로 보고 만들지 않는다(→ 실패 소식 → cart FROZEN)
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

    // 주문 생성 트랜잭션은 이미 롤백됐다. 실패 소식만 새 트랜잭션에서 아웃박스에 남긴다
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
