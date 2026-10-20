package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.cart.app.personal.CartFacade;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderItem;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderStatus;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderType;
import com.cartmate.cartmate.boundedContext.order.dto.personal.IndividualGroupOrderCreateRequest;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderCreateResponse;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.order.out.personal.OrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.dto.CartOrderItem;
import com.cartmate.cartmate.shared.cart.dto.IndividualGroupPurchaseOrderable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private CartFacade cartFacade;

    @Mock
    private IndividualGroupPurchaseFacade individualGroupPurchaseFacade;

    @InjectMocks
    private OrderCreateUseCase createUseCase;

    @Test
    @DisplayName("선택한 장바구니 상품을 현재 가격으로 주문하고 결제 기한은 30분 뒤다")
    void createPersonal() {
        List<Long> cartItemIds = List.of(11L, 12L);
        given(cartFacade.getCartItemsForOrder(MEMBER_ID, cartItemIds)).willReturn(List.of(
                new CartOrderItem(11L, 1L, 1L, 2),
                new CartOrderItem(12L, 2L, 4L, 1)));
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(1L, 1L, 7L, "축구 유니폼", "M / 레드", 39000, null, true),
                new OrderProductInfo(4L, 2L, 8L, "축구공", "5호", 25000, null, true)));
        given(orderNumberGenerator.generateUnique(any(), any())).willReturn(ORDER_NUMBER);
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        OrderCreateResponse response = createUseCase.createPersonal(MEMBER_ID, cartItemIds, ADDRESS, NOW);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        then(orderRepository).should().save(captor.capture());
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
    }

    @Test
    @DisplayName("구매할 수 없는 상품이 있으면 주문을 만들지 않는다")
    void rejectNotPurchasable() {
        given(cartFacade.getCartItemsForOrder(MEMBER_ID, List.of(15L)))
                .willReturn(List.of(new CartOrderItem(15L, 3L, 5L, 1)));
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(5L, 3L, 9L, "정강이 보호대", "FREE", 15000, null, false)));

        assertThatThrownBy(() -> createUseCase.createPersonal(MEMBER_ID, List.of(15L), ADDRESS, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_PRODUCT_NOT_PURCHASABLE.getMsg());
        then(orderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("공동구매 상품 주문의 결제 기한은 판매 마감이 더 이르면 판매 마감 시각이다")
    void createIndividualGroup() {
        LocalDateTime saleDeadlineAt = NOW.plusMinutes(10);
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(4L, 2L, 8L, "축구공", "5호", 25000, null, true)));
        given(individualGroupPurchaseFacade.getOrderable(PURCHASE_ID, List.of(2L), NOW))
                .willReturn(new IndividualGroupPurchaseOrderable(PURCHASE_ID, saleDeadlineAt));
        given(orderNumberGenerator.generateUnique(any(), any())).willReturn(ORDER_NUMBER);
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        OrderCreateResponse response = createUseCase.createIndividualGroup(MEMBER_ID, PURCHASE_ID,
                List.of(new IndividualGroupOrderCreateRequest.Item(4L, 3)), ADDRESS, NOW);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        then(orderRepository).should().save(captor.capture());
        Order order = captor.getValue();
        assertThat(order.getOrderType()).isEqualTo(OrderType.INDIVIDUAL_GROUP);
        assertThat(order.getIndividualGroupPurchaseId()).isEqualTo(PURCHASE_ID);
        assertThat(response.totalAmount()).isEqualTo(75000L);
        assertThat(response.paymentDeadlineAt()).isEqualTo(saleDeadlineAt);
    }

    @Test
    @DisplayName("판매가 마감된 공동구매에는 주문할 수 없다")
    void rejectClosedPurchase() {
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(4L, 2L, 8L, "축구공", "5호", 25000, null, true)));
        given(individualGroupPurchaseFacade.getOrderable(PURCHASE_ID, List.of(2L), NOW))
                .willThrow(new ServiceException(CartErrorCode.CART_INDIVIDUAL_SALE_CLOSED));

        assertThatThrownBy(() -> createUseCase.createIndividualGroup(MEMBER_ID, PURCHASE_ID,
                List.of(new IndividualGroupOrderCreateRequest.Item(4L, 1)), ADDRESS, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(CartErrorCode.CART_INDIVIDUAL_SALE_CLOSED.getMsg());
        then(orderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("같은 장바구니 상품이 든 결제 대기 주문이 있으면 다시 주문할 수 없다")
    void rejectPendingPersonal() {
        given(cartFacade.getCartItemsForOrder(MEMBER_ID, List.of(11L)))
                .willReturn(List.of(new CartOrderItem(11L, 1L, 1L, 2)));
        given(orderRepository.findPendingPersonalOrderIdContaining(MEMBER_ID, List.of(1L))).willReturn(Optional.of(99L));

        assertThatThrownBy(() -> createUseCase.createPersonal(MEMBER_ID, List.of(11L), ADDRESS, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_ALREADY_PENDING.getMsg());
        then(orderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("결제 대기 주문이 없으면(이전 주문이 결제 완료·취소) 같은 장바구니 상품을 다시 주문할 수 있다")
    void allowPersonalWhenNoPending() {
        given(cartFacade.getCartItemsForOrder(MEMBER_ID, List.of(11L)))
                .willReturn(List.of(new CartOrderItem(11L, 1L, 1L, 2)));
        given(orderRepository.findPendingPersonalOrderIdContaining(MEMBER_ID, List.of(1L))).willReturn(Optional.empty());
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(1L, 1L, 7L, "축구 유니폼", null, 39000, null, true)));
        given(orderNumberGenerator.generateUnique(any(), any())).willReturn(ORDER_NUMBER);
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        OrderCreateResponse response = createUseCase.createPersonal(MEMBER_ID, List.of(11L), ADDRESS, NOW);

        assertThat(response.totalAmount()).isEqualTo(78000L);
    }

    @Test
    @DisplayName("같은 공동구매에 결제 대기 주문이 있으면 다시 주문할 수 없다")
    void rejectPendingIndividualGroup() {
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(4L, 2L, 8L, "축구공", null, 25000, null, true)));
        given(individualGroupPurchaseFacade.getOrderable(PURCHASE_ID, List.of(2L), NOW))
                .willReturn(new IndividualGroupPurchaseOrderable(PURCHASE_ID, NOW.plusDays(1)));
        given(orderRepository.findPendingIndividualGroupOrderId(MEMBER_ID, PURCHASE_ID)).willReturn(Optional.of(99L));

        assertThatThrownBy(() -> createUseCase.createIndividualGroup(MEMBER_ID, PURCHASE_ID,
                List.of(new IndividualGroupOrderCreateRequest.Item(4L, 1)), ADDRESS, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_ALREADY_PENDING.getMsg());
        then(orderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("결제 대기 주문이 없으면(이전 주문이 결제 완료·취소) 같은 공동구매에 다시 주문할 수 있다")
    void allowIndividualGroupWhenNoPending() {
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(4L, 2L, 8L, "축구공", null, 25000, null, true)));
        given(individualGroupPurchaseFacade.getOrderable(PURCHASE_ID, List.of(2L), NOW))
                .willReturn(new IndividualGroupPurchaseOrderable(PURCHASE_ID, NOW.plusDays(1)));
        given(orderRepository.findPendingIndividualGroupOrderId(MEMBER_ID, PURCHASE_ID)).willReturn(Optional.empty());
        given(orderNumberGenerator.generateUnique(any(), any())).willReturn(ORDER_NUMBER);
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        OrderCreateResponse response = createUseCase.createIndividualGroup(MEMBER_ID, PURCHASE_ID,
                List.of(new IndividualGroupOrderCreateRequest.Item(4L, 1)), ADDRESS, NOW);

        assertThat(response.totalAmount()).isEqualTo(25000L);
    }
}
