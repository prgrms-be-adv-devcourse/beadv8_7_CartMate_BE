package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.IndividualGroupPurchaseReplica;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order.OrderLine;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.OrderItem;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.IndividualGroupOrderCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.OrderCreateResponse;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderCartClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.OrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.dto.CartOrderItem;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.shared.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
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
    private final IndividualGroupPurchaseReplicaUseCase individualGroupPurchaseReplicaUseCase;

    OrderCreateResponse createPersonal(Long memberId, List<Long> cartItemIds, DeliveryAddress deliveryAddress,
                                       LocalDateTime now) {
        List<CartOrderItem> cartItems = orderCartClient.getCartItemsForOrder(memberId, cartItemIds).stream()
                .sorted(Comparator.comparing(CartOrderItem::inventoryId))
                .toList();
        List<Long> inventoryIds = cartItems.stream().map(CartOrderItem::inventoryId).toList();
        if (orderRepository.findPendingPersonalOrderIdContaining(memberId, inventoryIds).isPresent()) {
            throw new ServiceException(OrderErrorCode.ORDER_ALREADY_PENDING);
        }
        Map<Long, OrderProductInfo> products = productsOf(inventoryIds);
        List<OrderLine> lines = cartItems.stream()
                .map(item -> toLine(products, item.inventoryId(), item.quantity()))
                .toList();
        Order order = Order.createPersonal(generateOrderNumber(now), memberId, lines, deliveryAddress, now);
        Order saved = saveNew(order);
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
        IndividualGroupPurchaseReplica purchase = individualGroupPurchaseReplicaUseCase.find(individualGroupPurchaseId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_GROUP_PURCHASE_NOT_FOUND));
        purchase.validateOrderable(productIds, now);
        if (orderRepository.findPendingIndividualGroupOrderId(memberId, individualGroupPurchaseId).isPresent()) {
            throw new ServiceException(OrderErrorCode.ORDER_ALREADY_PENDING);
        }
        Order order = Order.createIndividualGroup(generateOrderNumber(now), memberId,
                individualGroupPurchaseId, purchase.getDeadlineAt(), lines, deliveryAddress, now);
        Order saved = saveNew(order);
        eventPublisher.publish(toCreatedEvent(saved));
        return OrderCreateResponse.from(saved);
    }

    private OrderCreatedEvent toCreatedEvent(Order order) {
        return new OrderCreatedEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getBuyerMemberId(),
                order.getOrderType(),
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

    private Order saveNew(Order order) {
        try {
            return orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            if (isPendingItemConflict(e)) {
                throw new ServiceException(OrderErrorCode.ORDER_ALREADY_PENDING);
            }
            throw e;
        }
    }

    private static boolean isPendingItemConflict(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return isPendingConstraint(violation.getConstraintName());
            }
        }
        String message = e.getMostSpecificCause().getMessage();
        return message != null && isPendingConstraint(message);
    }

    private static boolean isPendingConstraint(String text) {
        return text.contains(OrderItem.PENDING_BUYER_INVENTORY_UNIQUE)
                || text.contains(Order.PENDING_BUYER_INDIVIDUAL_GROUP_UNIQUE);
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
