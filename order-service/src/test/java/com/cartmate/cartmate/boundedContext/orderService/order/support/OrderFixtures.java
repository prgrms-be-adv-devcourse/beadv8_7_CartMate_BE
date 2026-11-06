package com.cartmate.cartmate.boundedContext.orderService.order.support;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order.OrderLine;

import java.time.LocalDateTime;
import java.util.List;

public final class OrderFixtures {

    public static final String ORDER_NUMBER = "20261019-ABCDEFGH";

    private OrderFixtures() {
    }

    public static DeliveryAddress address() {
        return DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");
    }

    public static DeliveryAddress addressWithoutDetail() {
        return DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", null);
    }

    public static OrderLine ballLine(Long sellerId, int quantity) {
        return new OrderLine(2L, 4L, sellerId, "축구공", "5호", 25000, quantity);
    }

    public static OrderLine productALine(Long productId, int quantity) {
        return new OrderLine(productId, 1L, 7L, "상품A", "옵션A", 25000, quantity);
    }

    public static OrderLine productBLine(Long productId, int quantity) {
        return new OrderLine(productId, 2L, 7L, "상품B", null, 15000, quantity);
    }

    public static Order personalOrder(Long buyerMemberId, OrderLine line, LocalDateTime createdAt) {
        return Order.createPersonal(ORDER_NUMBER, buyerMemberId, List.of(line), addressWithoutDetail(), createdAt);
    }

    public static Order personalOrder(String orderNumber, Long buyerMemberId, List<OrderLine> lines,
                                      LocalDateTime createdAt) {
        return Order.createPersonal(orderNumber, buyerMemberId, lines, address(), createdAt);
    }

    public static Order individualGroupOrder(String orderNumber, Long buyerMemberId, Long individualGroupPurchaseId,
                                             List<OrderLine> lines, LocalDateTime createdAt) {
        return Order.createIndividualGroup(orderNumber, buyerMemberId, individualGroupPurchaseId,
                createdAt.plusDays(1), lines, address(), createdAt);
    }

    public static OrderItemLine sharedLine(Long productId, Long inventoryId, Long sellerId, int quantity,
                                           long unitPrice) {
        return new OrderItemLine(productId, inventoryId, sellerId, "상품", null, quantity, unitPrice);
    }

    public static SharedGroupOrder sharedOrder(Long creatorMemberId, Long memberId, LocalDateTime closedAt) {
        return sharedOrder(10L, creatorMemberId, memberId, closedAt, sharedLine(2L, 4L, null, 2, 25000L));
    }

    public static SharedGroupOrder sharedOrder(Long sharedGroupPurchaseId, Long creatorMemberId, Long memberId,
                                               LocalDateTime closedAt, OrderItemLine... lines) {
        return SharedGroupOrder.create(sharedGroupPurchaseId, creatorMemberId, List.of(creatorMemberId, memberId),
                List.of(lines), closedAt);
    }
}
