package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.IndividualGroupPurchaseReplica;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.OrderItem;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.shared.order.OrderType;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrderCreateUseCaseTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PURCHASE_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);
    private static final String ORDER_NUMBER = "20261019-ABCDEFGH";
    private static final DeliveryAddress ADDRESS =
            DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderProductClient orderProductClient;

    @Mock
    private OrderNumberGenerator orderNumberGenerator;

    @Mock
    private OrderCartClient orderCartClient;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private IndividualGroupPurchaseReplicaUseCase replicaUseCase;

    @InjectMocks
    private OrderCreateUseCase createUseCase;

    @Test
    @DisplayName("선택한 장바구니 상품을 현재 가격으로 주문하고 결제 기한은 30분 뒤다")
    void createPersonal() {
        List<Long> cartItemIds = List.of(11L, 12L);
        given(orderCartClient.getCartItemsForOrder(MEMBER_ID, cartItemIds)).willReturn(List.of(
                new CartOrderItem(11L, 1L, 1L, 2),
                new CartOrderItem(12L, 2L, 4L, 1)));
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(1L, 1L, 7L, "축구 유니폼", "M / 레드", 39000, null, true),
                new OrderProductInfo(4L, 2L, 8L, "축구공", "5호", 25000, null, true)));
        given(orderNumberGenerator.generateUnique(any(), any())).willReturn(ORDER_NUMBER);
        given(orderRepository.saveAndFlush(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        OrderCreateResponse response = createUseCase.createPersonal(MEMBER_ID, cartItemIds, ADDRESS, NOW);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        then(orderRepository).should().saveAndFlush(captor.capture());
        Order order = captor.getValue();
        assertThat(order.getOrderType()).isEqualTo(OrderType.PERSONAL);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.getBuyerMemberId()).isEqualTo(MEMBER_ID);
        assertThat(order.getItems())
                .extracting(OrderItem::getProductId, OrderItem::getInventoryId, OrderItem::getSellerId,
                        OrderItem::getUnitPrice, OrderItem::getQuantity)
                .containsExactly(tuple(1L, 1L, 7L, 39000L, 2), tuple(2L, 4L, 8L, 25000L, 1));
        assertThat(response.orderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(response.totalAmount()).isEqualTo(103000L);
        assertThat(response.paymentDeadlineAt()).isEqualTo(NOW.plus(Order.PAYMENT_PERIOD));
        ArgumentCaptor<OrderCreatedEvent> event = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        then(eventPublisher).should().publish(event.capture());
        assertThat(event.getValue().orderType()).isEqualTo(OrderType.PERSONAL);
        assertThat(event.getValue().memberId()).isEqualTo(MEMBER_ID);
        assertThat(event.getValue().items()).extracting(OrderCreatedEvent.Item::inventoryId).containsExactly(1L, 4L);
    }

    @Test
    @DisplayName("구매할 수 없는 상품이 있으면 주문을 만들지 않는다")
    void rejectNotPurchasable() {
        given(orderCartClient.getCartItemsForOrder(MEMBER_ID, List.of(15L)))
                .willReturn(List.of(new CartOrderItem(15L, 3L, 5L, 1)));
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(5L, 3L, 9L, "정강이 보호대", "FREE", 15000, null, false)));

        assertThatThrownBy(() -> createUseCase.createPersonal(MEMBER_ID, List.of(15L), ADDRESS, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_PRODUCT_NOT_PURCHASABLE.getMsg());
        then(orderRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("공동구매 상품 주문의 결제 기한은 판매 마감이 더 이르면 판매 마감 시각이다")
    void createIndividualGroup() {
        LocalDateTime saleDeadlineAt = NOW.plusMinutes(10);
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(4L, 2L, 8L, "축구공", "5호", 25000, null, true)));
        given(replicaUseCase.find(PURCHASE_ID)).willReturn(Optional.of(openPurchase(saleDeadlineAt)));
        given(orderNumberGenerator.generateUnique(any(), any())).willReturn(ORDER_NUMBER);
        given(orderRepository.saveAndFlush(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        OrderCreateResponse response = createUseCase.createIndividualGroup(MEMBER_ID, PURCHASE_ID,
                List.of(new IndividualGroupOrderCreateRequest.Item(4L, 3)), ADDRESS, NOW);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        then(orderRepository).should().saveAndFlush(captor.capture());
        Order order = captor.getValue();
        assertThat(order.getOrderType()).isEqualTo(OrderType.INDIVIDUAL_GROUP);
        assertThat(order.getIndividualGroupPurchaseId()).isEqualTo(PURCHASE_ID);
        assertThat(response.totalAmount()).isEqualTo(75000L);
        assertThat(response.paymentDeadlineAt()).isEqualTo(saleDeadlineAt);
        ArgumentCaptor<OrderCreatedEvent> event = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        then(eventPublisher).should().publish(event.capture());
        assertThat(event.getValue().orderType()).isEqualTo(OrderType.INDIVIDUAL_GROUP);
        assertThat(event.getValue().individualGroupPurchaseId()).isEqualTo(PURCHASE_ID);
        assertThat(event.getValue().items()).extracting(OrderCreatedEvent.Item::productId, OrderCreatedEvent.Item::quantity)
                .containsExactly(tuple(2L, 3));
    }

    @Test
    @DisplayName("판매가 마감된 공동구매에는 주문할 수 없다")
    void rejectClosedPurchase() {
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(4L, 2L, 8L, "축구공", "5호", 25000, null, true)));
        given(replicaUseCase.find(PURCHASE_ID)).willReturn(Optional.of(IndividualGroupPurchaseReplica.of(
                PURCHASE_ID, NOW.plusDays(1), true, NOW.minusMinutes(1), List.of(2L), 3)));

        assertThatThrownBy(() -> createUseCase.createIndividualGroup(MEMBER_ID, PURCHASE_ID,
                List.of(new IndividualGroupOrderCreateRequest.Item(4L, 1)), ADDRESS, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_GROUP_PURCHASE_SALE_CLOSED.getMsg());
        then(orderRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("같은 장바구니 상품이 든 결제 대기 주문이 있으면 다시 주문할 수 없다")
    void rejectPendingPersonal() {
        given(orderCartClient.getCartItemsForOrder(MEMBER_ID, List.of(11L)))
                .willReturn(List.of(new CartOrderItem(11L, 1L, 1L, 2)));
        given(orderRepository.findPendingPersonalOrderIdContaining(MEMBER_ID, List.of(1L))).willReturn(Optional.of(99L));

        assertThatThrownBy(() -> createUseCase.createPersonal(MEMBER_ID, List.of(11L), ADDRESS, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_ALREADY_PENDING.getMsg());
        then(orderRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("조회 뒤 다른 요청이 같은 상품의 결제 대기 주문을 먼저 저장하면 DB unique 위반을 '이미 결제 대기 중'으로 바꾼다")
    void translatePendingUniqueViolation() {
        given(orderCartClient.getCartItemsForOrder(MEMBER_ID, List.of(11L)))
                .willReturn(List.of(new CartOrderItem(11L, 1L, 1L, 2)));
        given(orderRepository.findPendingPersonalOrderIdContaining(MEMBER_ID, List.of(1L))).willReturn(Optional.empty());
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(1L, 1L, 7L, "축구 유니폼", null, 39000, null, true)));
        given(orderNumberGenerator.generateUnique(any(), any())).willReturn(ORDER_NUMBER);
        given(orderRepository.saveAndFlush(any(Order.class))).willThrow(new DataIntegrityViolationException(
                "Duplicate entry '1-1' for key 'order_item." + OrderItem.PENDING_BUYER_INVENTORY_UNIQUE + "'"));

        assertThatThrownBy(() -> createUseCase.createPersonal(MEMBER_ID, List.of(11L), ADDRESS, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_ALREADY_PENDING.getMsg());
    }

    @Test
    @DisplayName("다른 제약 위반은 그대로 올린다")
    void rethrowOtherIntegrityViolation() {
        given(orderCartClient.getCartItemsForOrder(MEMBER_ID, List.of(11L)))
                .willReturn(List.of(new CartOrderItem(11L, 1L, 1L, 2)));
        given(orderRepository.findPendingPersonalOrderIdContaining(MEMBER_ID, List.of(1L))).willReturn(Optional.empty());
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(1L, 1L, 7L, "축구 유니폼", null, 39000, null, true)));
        given(orderNumberGenerator.generateUnique(any(), any())).willReturn(ORDER_NUMBER);
        given(orderRepository.saveAndFlush(any(Order.class))).willThrow(new DataIntegrityViolationException(
                "Duplicate entry for key 'orders.uk_orders_order_number'"));

        assertThatThrownBy(() -> createUseCase.createPersonal(MEMBER_ID, List.of(11L), ADDRESS, NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("결제 대기 주문이 없으면(이전 주문이 결제 완료·취소) 같은 장바구니 상품을 다시 주문할 수 있다")
    void allowPersonalWhenNoPending() {
        given(orderCartClient.getCartItemsForOrder(MEMBER_ID, List.of(11L)))
                .willReturn(List.of(new CartOrderItem(11L, 1L, 1L, 2)));
        given(orderRepository.findPendingPersonalOrderIdContaining(MEMBER_ID, List.of(1L))).willReturn(Optional.empty());
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(1L, 1L, 7L, "축구 유니폼", null, 39000, null, true)));
        given(orderNumberGenerator.generateUnique(any(), any())).willReturn(ORDER_NUMBER);
        given(orderRepository.saveAndFlush(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        OrderCreateResponse response = createUseCase.createPersonal(MEMBER_ID, List.of(11L), ADDRESS, NOW);

        assertThat(response.totalAmount()).isEqualTo(78000L);
    }

    @Test
    @DisplayName("같은 공동구매에 결제 대기 주문이 있으면 다시 주문할 수 없다")
    void rejectPendingIndividualGroup() {
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(4L, 2L, 8L, "축구공", null, 25000, null, true)));
        given(replicaUseCase.find(PURCHASE_ID)).willReturn(Optional.of(openPurchase(NOW.plusDays(1))));
        given(orderRepository.findPendingIndividualGroupOrderId(MEMBER_ID, PURCHASE_ID)).willReturn(Optional.of(99L));

        assertThatThrownBy(() -> createUseCase.createIndividualGroup(MEMBER_ID, PURCHASE_ID,
                List.of(new IndividualGroupOrderCreateRequest.Item(4L, 1)), ADDRESS, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_ALREADY_PENDING.getMsg());
        then(orderRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("결제 대기 주문이 없으면(이전 주문이 결제 완료·취소) 같은 공동구매에 다시 주문할 수 있다")
    void allowIndividualGroupWhenNoPending() {
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(4L, 2L, 8L, "축구공", null, 25000, null, true)));
        given(replicaUseCase.find(PURCHASE_ID)).willReturn(Optional.of(openPurchase(NOW.plusDays(1))));
        given(orderRepository.findPendingIndividualGroupOrderId(MEMBER_ID, PURCHASE_ID)).willReturn(Optional.empty());
        given(orderNumberGenerator.generateUnique(any(), any())).willReturn(ORDER_NUMBER);
        given(orderRepository.saveAndFlush(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        OrderCreateResponse response = createUseCase.createIndividualGroup(MEMBER_ID, PURCHASE_ID,
                List.of(new IndividualGroupOrderCreateRequest.Item(4L, 1)), ADDRESS, NOW);

        assertThat(response.totalAmount()).isEqualTo(25000L);
    }

    @Test
    @DisplayName("같은 재고 상품이 두 번 담기면 상품 조회 없이 주문을 거절한다")
    void rejectDuplicateIndividualGroupItem() {
        assertThatThrownBy(() -> createUseCase.createIndividualGroup(MEMBER_ID, PURCHASE_ID,
                List.of(new IndividualGroupOrderCreateRequest.Item(4L, 1),
                        new IndividualGroupOrderCreateRequest.Item(4L, 2)), ADDRESS, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_DUPLICATE_ITEM.getMsg());
        then(orderProductClient).should(never()).findAllByInventoryIds(anyCollection());
        then(replicaUseCase).should(never()).find(any());
        then(orderRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("서로 다른 재고 상품은 각각 주문 줄로 만든다")
    void createIndividualGroupWithDistinctItems() {
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(4L, 2L, 8L, "축구공", "5호", 25000, null, true),
                new OrderProductInfo(5L, 2L, 8L, "축구공", "4호", 23000, null, true)));
        given(replicaUseCase.find(PURCHASE_ID)).willReturn(Optional.of(openPurchase(NOW.plusDays(1))));
        given(orderRepository.findPendingIndividualGroupOrderId(MEMBER_ID, PURCHASE_ID)).willReturn(Optional.empty());
        given(orderNumberGenerator.generateUnique(any(), any())).willReturn(ORDER_NUMBER);
        given(orderRepository.saveAndFlush(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        OrderCreateResponse response = createUseCase.createIndividualGroup(MEMBER_ID, PURCHASE_ID,
                List.of(new IndividualGroupOrderCreateRequest.Item(4L, 1),
                        new IndividualGroupOrderCreateRequest.Item(5L, 2)), ADDRESS, NOW);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        then(orderRepository).should().saveAndFlush(captor.capture());
        assertThat(captor.getValue().getItems())
                .extracting(OrderItem::getInventoryId, OrderItem::getQuantity)
                .containsExactly(tuple(4L, 1), tuple(5L, 2));
        assertThat(response.totalAmount()).isEqualTo(71000L);
    }

    @Test
    @DisplayName("공동구매가 사본에도 cart에도 없으면 주문할 수 없다")
    void rejectUnknownPurchase() {
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(4L, 2L, 8L, "축구공", null, 25000, null, true)));
        given(replicaUseCase.find(PURCHASE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> createUseCase.createIndividualGroup(MEMBER_ID, PURCHASE_ID,
                List.of(new IndividualGroupOrderCreateRequest.Item(4L, 1)), ADDRESS, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_GROUP_PURCHASE_NOT_FOUND.getMsg());
        then(orderRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("공동구매에서 팔지 않는 상품은 주문할 수 없다")
    void rejectProductNotSold() {
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(4L, 3L, 8L, "농구공", null, 25000, null, true)));
        given(replicaUseCase.find(PURCHASE_ID)).willReturn(Optional.of(openPurchase(NOW.plusDays(1))));

        assertThatThrownBy(() -> createUseCase.createIndividualGroup(MEMBER_ID, PURCHASE_ID,
                List.of(new IndividualGroupOrderCreateRequest.Item(4L, 1)), ADDRESS, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_GROUP_PURCHASE_PRODUCT_NOT_SOLD.getMsg());
        then(orderRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("조회 뒤 같은 공동구매 결제 대기 주문이 먼저 저장되면 orders unique 위반을 '이미 결제 대기 중'으로 바꾼다")
    void translatePendingIndividualGroupUniqueViolation() {
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(4L, 2L, 8L, "축구공", null, 25000, null, true)));
        given(replicaUseCase.find(PURCHASE_ID)).willReturn(Optional.of(openPurchase(NOW.plusDays(1))));
        given(orderRepository.findPendingIndividualGroupOrderId(MEMBER_ID, PURCHASE_ID)).willReturn(Optional.empty());
        given(orderNumberGenerator.generateUnique(any(), any())).willReturn(ORDER_NUMBER);
        given(orderRepository.saveAndFlush(any(Order.class))).willThrow(new DataIntegrityViolationException(
                "Duplicate entry '1-10' for key 'orders." + Order.PENDING_BUYER_INDIVIDUAL_GROUP_UNIQUE + "'"));

        assertThatThrownBy(() -> createUseCase.createIndividualGroup(MEMBER_ID, PURCHASE_ID,
                List.of(new IndividualGroupOrderCreateRequest.Item(4L, 1)), ADDRESS, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_ALREADY_PENDING.getMsg());
    }

    private static IndividualGroupPurchaseReplica openPurchase(LocalDateTime deadlineAt) {
        return IndividualGroupPurchaseReplica.of(PURCHASE_ID, deadlineAt, false, null, List.of(2L), 0);
    }
}
