package com.cartmate.cartmate.boundedContext.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderItem;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderParticipant;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.event.DomainEventPublisher;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCreatedEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCreationFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
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
class SharedGroupOrderCreateUseCaseTest {

    private static final Long PURCHASE_ID = 10L;
    private static final Long CREATOR = 1L;
    private static final LocalDateTime CLOSED_AT = LocalDateTime.of(2026, 10, 19, 12, 0);
    private static final Duration CREATION_WINDOW = Duration.ofMinutes(8);
    private static final LocalDateTime NOW = CLOSED_AT.plusSeconds(1);

    @Mock
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    @Mock
    private OrderProductClient orderProductClient;

    @Mock
    private DomainEventPublisher eventPublisher;

    private SharedGroupOrderCreateUseCase createUseCase;

    @BeforeEach
    void setUp() {
        createUseCase = new SharedGroupOrderCreateUseCase(
                sharedGroupOrderRepository, orderProductClient, eventPublisher, CREATION_WINDOW);
    }

    @Test
    @DisplayName("마감 시점 가격으로 주문을 만들고 N빵하며 나머지 원은 생성자가 부담한다")
    void createWithPriceSnapshot() {
        given(sharedGroupOrderRepository.findBySharedGroupPurchaseId(PURCHASE_ID)).willReturn(Optional.empty());
        given(sharedGroupOrderRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(4L, 2L, 1L, "축구공", "5호", 25000, null, true),
                new OrderProductInfo(1L, 1L, 1L, "축구 유니폼", "M / 레드", 39000, null, true)));

        createUseCase.createFromClosedPurchase(event(List.of(1L, 2L, 3L),
                new SharedGroupPurchaseClosedEvent.Item(2L, 4L, 2),
                new SharedGroupPurchaseClosedEvent.Item(1L, 1L, 1)), NOW);

        ArgumentCaptor<SharedGroupOrder> captor = ArgumentCaptor.forClass(SharedGroupOrder.class);
        then(sharedGroupOrderRepository).should().save(captor.capture());
        SharedGroupOrder order = captor.getValue();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.PAYING);
        assertThat(order.getTotalAmount()).isEqualTo(89000L);
        assertThat(order.getPaymentDeadlineAt()).isEqualTo(CLOSED_AT.plus(SharedGroupOrder.PAYMENT_PERIOD));
        assertThat(order.getItems()).extracting(SharedGroupOrderItem::getUnitPrice).containsExactly(25000L, 39000L);
        assertThat(order.getItems())
                .extracting(SharedGroupOrderItem::getSellerId, SharedGroupOrderItem::getProductName, SharedGroupOrderItem::getOptionName)
                .containsExactly(tuple(1L, "축구공", "5호"), tuple(1L, "축구 유니폼", "M / 레드"));
        assertThat(order.getParticipant(CREATOR).getShareAmount()).isEqualTo(29668L);
        assertThat(order.getParticipant(2L).getShareAmount()).isEqualTo(29666L);
        assertThat(order.getParticipants()).extracting(SharedGroupOrderParticipant::getShareAmount)
                .containsExactly(29668L, 29666L, 29666L);
        then(eventPublisher).should().publish(any(SharedGroupOrderCreatedEvent.class));
    }

    @Test
    @DisplayName("이미 같은 공동구매로 만든 주문이 있으면 다시 만들지 않고, 그 주문으로 생성 소식만 다시 낸다")
    void skipWhenAlreadyCreated() {
        SharedGroupOrder existing = SharedGroupOrder.create(PURCHASE_ID, CREATOR, List.of(CREATOR),
                List.of(new SharedGroupOrder.OrderItemLine(2L, 4L, 1L, "축구공", "5호", 1, 25000)), CLOSED_AT);
        ReflectionTestUtils.setField(existing, "id", 99L);
        given(sharedGroupOrderRepository.findBySharedGroupPurchaseId(PURCHASE_ID)).willReturn(Optional.of(existing));

        createUseCase.createFromClosedPurchase(event(List.of(1L), new SharedGroupPurchaseClosedEvent.Item(2L, 4L, 1)), NOW);

        then(sharedGroupOrderRepository).should(never()).save(any());
        then(orderProductClient).shouldHaveNoInteractions();
        ArgumentCaptor<SharedGroupOrderCreatedEvent> captor = ArgumentCaptor.forClass(SharedGroupOrderCreatedEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().sharedGroupOrderId()).isEqualTo(99L);
        assertThat(captor.getValue().sharedGroupPurchaseId()).isEqualTo(PURCHASE_ID);
    }

    @Test
    @DisplayName("상품 가격을 찾을 수 없으면 주문을 만들지 않는다")
    void productNotFound() {
        given(sharedGroupOrderRepository.findBySharedGroupPurchaseId(PURCHASE_ID)).willReturn(Optional.empty());
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of());

        assertThatThrownBy(() -> createUseCase.createFromClosedPurchase(
                event(List.of(1L), new SharedGroupPurchaseClosedEvent.Item(2L, 4L, 1)), NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_PRODUCT_NOT_FOUND.getMsg());
        then(sharedGroupOrderRepository).should(never()).save(any());
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("마감 뒤 생성 창(8분)을 넘긴 마감 이벤트로는 새로 만들지 않는다. 이미 있는 주문의 생성 소식은 창과 상관없이 다시 낸다")
    void expiredClosedEventIsRejected() {
        given(sharedGroupOrderRepository.findBySharedGroupPurchaseId(PURCHASE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> createUseCase.createFromClosedPurchase(
                event(List.of(1L), new SharedGroupPurchaseClosedEvent.Item(2L, 4L, 1)),
                CLOSED_AT.plus(CREATION_WINDOW).plusSeconds(1)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_SHARED_CREATION_EXPIRED.getMsg());
        then(sharedGroupOrderRepository).should(never()).save(any());
        then(orderProductClient).shouldHaveNoInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("생성 실패 소식은 공동구매 ID와 실패 코드를 담아 발행한다")
    void reportCreationFailed() {
        createUseCase.reportCreationFailed(PURCHASE_ID, "ORDER_PRODUCT_NOT_FOUND");

        then(eventPublisher).should().publish(new SharedGroupOrderCreationFailedEvent(PURCHASE_ID, "ORDER_PRODUCT_NOT_FOUND"));
    }

    private SharedGroupPurchaseClosedEvent event(List<Long> participants, SharedGroupPurchaseClosedEvent.Item... items) {
        return new SharedGroupPurchaseClosedEvent(PURCHASE_ID, CREATOR, participants, List.of(items), CLOSED_AT);
    }
}
