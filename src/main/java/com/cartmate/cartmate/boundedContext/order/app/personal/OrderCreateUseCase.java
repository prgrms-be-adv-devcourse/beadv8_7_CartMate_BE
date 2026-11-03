package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order.OrderLine;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderItem;
import com.cartmate.cartmate.boundedContext.order.dto.personal.IndividualGroupOrderCreateRequest;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderCreateResponse;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.OrderCartClient;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.order.out.personal.OrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.dto.CartOrderItem;
import com.cartmate.cartmate.shared.cart.dto.IndividualGroupPurchaseOrderable;
import com.cartmate.cartmate.shared.event.DomainEventPublisher;
import com.cartmate.cartmate.shared.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class OrderCreateUseCase {

    private final OrderRepository orderRepository;
    private final OrderProductClient orderProductClient;
    private final OrderNumberGenerator orderNumberGenerator;
    private final OrderCartClient orderCartClient;
    private final DomainEventPublisher eventPublisher;

    OrderCreateResponse createPersonal(Long memberId, List<Long> cartItemIds, DeliveryAddress deliveryAddress,
                                       LocalDateTime now) {
        List<CartOrderItem> cartItems = orderCartClient.getCartItemsForOrder(memberId, cartItemIds);
        List<Long> inventoryIds = cartItems.stream().map(CartOrderItem::inventoryId).toList();
        if (orderRepository.findPendingPersonalOrderIdContaining(memberId, inventoryIds).isPresent()) {
            throw new ServiceException(OrderErrorCode.ORDER_ALREADY_PENDING);
        }
        Map<Long, OrderProductInfo> products = productsOf(inventoryIds);
        List<OrderLine> lines = cartItems.stream()
                .map(item -> toLine(products, item.inventoryId(), item.quantity()))
                .toList();
        Order order = Order.createPersonal(generateOrderNumber(now), memberId, lines, deliveryAddress, now);
        Order saved = savePersonal(order);
        eventPublisher.publish(toCreatedEvent(saved));
        return OrderCreateResponse.from(saved);
    }

    OrderCreateResponse createIndividualGroup(Long memberId, Long individualGroupPurchaseId,
                                              List<IndividualGroupOrderCreateRequest.Item> items,
                                              DeliveryAddress deliveryAddress, LocalDateTime now) {
        List<Long> inventoryIds = items.stream().map(IndividualGroupOrderCreateRequest.Item::inventoryId).toList();
        if (inventoryIds.stream().distinct().count() != inventoryIds.size()) {
            throw new ServiceException(OrderErrorCode.ORDER_DUPLICATE_ITEM);
        }
        Map<Long, OrderProductInfo> products = productsOf(inventoryIds);
        List<OrderLine> lines = items.stream()
                .map(item -> toLine(products, item.inventoryId(), item.quantity()))
                .toList();
        List<Long> productIds = lines.stream().map(OrderLine::productId).distinct().toList();
        IndividualGroupPurchaseOrderable orderable =
                orderCartClient.getIndividualGroupPurchaseOrderable(individualGroupPurchaseId, productIds, now);
        if (orderRepository.findPendingIndividualGroupOrderId(memberId, orderable.individualGroupPurchaseId()).isPresent()) {
            throw new ServiceException(OrderErrorCode.ORDER_ALREADY_PENDING);
        }
        Order order = Order.createIndividualGroup(generateOrderNumber(now), memberId,
                orderable.individualGroupPurchaseId(), orderable.saleDeadlineAt(), lines, deliveryAddress, now);
        Order saved = orderRepository.save(order);
        eventPublisher.publish(toCreatedEvent(saved));
        return OrderCreateResponse.from(saved);
    }

    private OrderCreatedEvent toCreatedEvent(Order order) {
        return new OrderCreatedEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getBuyerMemberId(),
                order.getOrderType().name(),
                order.getIndividualGroupPurchaseId(),
                order.getCreatedAt(),
                order.getItems().stream()
                        .map(item -> new OrderCreatedEvent.Item(
                                item.getId(),
                                item.getProductId(),
                                item.getInventoryId(),
                                item.getSellerId(),
                                item.getQuantity(),
                                item.getUnitPrice()))
                        .toList()
        );
    }

    // 위 조회와 저장 사이에 같은 상품의 결제 대기 주문이 먼저 들어오면 order_item의 unique가 막는다.
    // 지금은 cart 행 락이 같은 회원의 주문 생성을 줄 세우지만, cart가 다른 서비스가 되면 그 락은 이 트랜잭션에 없다.
    private Order savePersonal(Order order) {
        try {
            return orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            if (isPendingItemConflict(e)) {
                throw new ServiceException(OrderErrorCode.ORDER_ALREADY_PENDING);
            }
            throw e;
        }
    }

    // Hibernate가 알려 주는 제약 이름을 먼저 보고, 없으면 DB 메시지에서 찾는다 (MySQL은 'order_item.uk_...'처럼 테이블을 붙인다)
    private static boolean isPendingItemConflict(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return violation.getConstraintName().contains(OrderItem.PENDING_BUYER_INVENTORY_UNIQUE);
            }
        }
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(OrderItem.PENDING_BUYER_INVENTORY_UNIQUE);
    }

    private String generateOrderNumber(LocalDateTime now) {
        return orderNumberGenerator.generateUnique(now.toLocalDate(), orderRepository::existsByOrderNumber);
    }

    private Map<Long, OrderProductInfo> productsOf(List<Long> inventoryIds) {
        return orderProductClient.findAllByInventoryIds(inventoryIds).stream()
                .collect(Collectors.toMap(OrderProductInfo::inventoryId, Function.identity()));
    }

    private OrderLine toLine(Map<Long, OrderProductInfo> products, Long inventoryId, int quantity) {
        OrderProductInfo product = products.get(inventoryId);
        if (product == null) {
            throw new ServiceException(OrderErrorCode.ORDER_PRODUCT_NOT_FOUND);
        }
        if (!product.purchasable()) {
            throw new ServiceException(OrderErrorCode.ORDER_PRODUCT_NOT_PURCHASABLE);
        }
        return new OrderLine(
                product.productId(),
                inventoryId,
                product.sellerId(),
                product.productName(),
                product.optionName(),
                product.price(),
                quantity
        );
    }
}
