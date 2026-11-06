package com.cartmate.cartmate.boundedContext.orderService.order.support;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order.OrderLine;

import java.time.LocalDateTime;
import java.util.List;

/**
 * order 테스트가 함께 쓰는 주문 · 배송지 픽스처. 시각은 모두 인자로 받는다.
 */
public final class OrderFixtures {

    /** 개인 주문 기본 주문번호 */
    public static final String ORDER_NUMBER = "20261019-ABCDEFGH";

    private OrderFixtures() {
    }

    /** 상세 주소(101호)까지 있는 배송지 */
    public static DeliveryAddress address() {
        return DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");
    }

    /** 상세 주소가 없는 배송지 */
    public static DeliveryAddress addressWithoutDetail() {
        return DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", null);
    }

    /** 축구공 5호(상품 2 · 재고 4, 25,000원) 주문 줄 */
    public static OrderLine ballLine(Long sellerId, int quantity) {
        return new OrderLine(2L, 4L, sellerId, "축구공", "5호", 25000, quantity);
    }

    /** 상품A · 옵션A(재고 1, 판매자 7, 25,000원) 주문 줄 */
    public static OrderLine productALine(Long productId, int quantity) {
        return new OrderLine(productId, 1L, 7L, "상품A", "옵션A", 25000, quantity);
    }

    /** 상품B · 옵션 없음(재고 2, 판매자 7, 15,000원) 주문 줄 */
    public static OrderLine productBLine(Long productId, int quantity) {
        return new OrderLine(productId, 2L, 7L, "상품B", null, 15000, quantity);
    }

    /** 기본 주문번호 · 상세 주소 없는 배송지로 만든 개인 주문 */
    public static Order personalOrder(Long buyerMemberId, OrderLine line, LocalDateTime createdAt) {
        return Order.createPersonal(ORDER_NUMBER, buyerMemberId, List.of(line), addressWithoutDetail(), createdAt);
    }

    /** 상세 주소(101호)가 있는 배송지로 만든 개인 주문 */
    public static Order personalOrder(String orderNumber, Long buyerMemberId, List<OrderLine> lines,
                                      LocalDateTime createdAt) {
        return Order.createPersonal(orderNumber, buyerMemberId, lines, address(), createdAt);
    }

    /** 판매 마감이 생성 하루 뒤이고, 상세 주소(101호)가 있는 배송지로 만든 개인 공동구매 주문 */
    public static Order individualGroupOrder(String orderNumber, Long buyerMemberId, Long individualGroupPurchaseId,
                                             List<OrderLine> lines, LocalDateTime createdAt) {
        return Order.createIndividualGroup(orderNumber, buyerMemberId, individualGroupPurchaseId,
                createdAt.plusDays(1), lines, address(), createdAt);
    }

    /** 이름이 "상품"이고 옵션이 없는 공동 주문 줄 */
    public static OrderItemLine sharedLine(Long productId, Long inventoryId, Long sellerId, int quantity,
                                           long unitPrice) {
        return new OrderItemLine(productId, inventoryId, sellerId, "상품", null, quantity, unitPrice);
    }

    /** 공동구매 10번에서 상품 2 · 재고 4를 2개(25,000원) 담은, 방장과 참여자 한 명의 공동 주문 */
    public static SharedGroupOrder sharedOrder(Long creatorMemberId, Long memberId, LocalDateTime closedAt) {
        return sharedOrder(10L, creatorMemberId, memberId, closedAt, sharedLine(2L, 4L, null, 2, 25000L));
    }

    /** 방장과 참여자 한 명이 함께하는 공동 주문 */
    public static SharedGroupOrder sharedOrder(Long sharedGroupPurchaseId, Long creatorMemberId, Long memberId,
                                               LocalDateTime closedAt, OrderItemLine... lines) {
        return SharedGroupOrder.create(sharedGroupPurchaseId, creatorMemberId, List.of(creatorMemberId, memberId),
                List.of(lines), closedAt);
    }
}
