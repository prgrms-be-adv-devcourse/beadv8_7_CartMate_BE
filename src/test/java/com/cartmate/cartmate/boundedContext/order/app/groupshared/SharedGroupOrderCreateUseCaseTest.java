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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Mock
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    @Mock
    private OrderProductClient orderProductClient;

    @InjectMocks
    private SharedGroupOrderCreateUseCase createUseCase;

    @Test
    @DisplayName("마감 시점 가격으로 주문을 만들고 N빵하며 나머지 원은 생성자가 부담한다")
    void createWithPriceSnapshot() {
        given(sharedGroupOrderRepository.existsBySharedGroupPurchaseId(PURCHASE_ID)).willReturn(false);
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of(
                new OrderProductInfo(4L, 2L, 1L, "축구공", "5호", 25000, null, true),
                new OrderProductInfo(1L, 1L, 1L, "축구 유니폼", "M / 레드", 39000, null, true)));

        createUseCase.createFromClosedPurchase(event(List.of(1L, 2L, 3L),
                new SharedGroupPurchaseClosedEvent.Item(2L, 4L, 2),
                new SharedGroupPurchaseClosedEvent.Item(1L, 1L, 1)));

        ArgumentCaptor<SharedGroupOrder> captor = ArgumentCaptor.forClass(SharedGroupOrder.class);
        then(sharedGroupOrderRepository).should().save(captor.capture());
        SharedGroupOrder order = captor.getValue();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.PAYING);
        assertThat(order.getTotalAmount()).isEqualTo(89000L);
        assertThat(order.getPaymentDeadlineAt()).isEqualTo(CLOSED_AT.plus(SharedGroupOrder.PAYMENT_PERIOD));
        assertThat(order.getItems()).extracting(SharedGroupOrderItem::getUnitPrice).containsExactly(25000L, 39000L);
        assertThat(order.getParticipant(CREATOR).getShareAmount()).isEqualTo(29668L);
        assertThat(order.getParticipant(2L).getShareAmount()).isEqualTo(29666L);
        assertThat(order.getParticipants()).extracting(SharedGroupOrderParticipant::getShareAmount)
                .containsExactly(29668L, 29666L, 29666L);
    }

    @Test
    @DisplayName("이미 같은 공동구매로 만든 주문이 있으면 다시 만들지 않는다")
    void skipWhenAlreadyCreated() {
        given(sharedGroupOrderRepository.existsBySharedGroupPurchaseId(PURCHASE_ID)).willReturn(true);

        createUseCase.createFromClosedPurchase(event(List.of(1L), new SharedGroupPurchaseClosedEvent.Item(2L, 4L, 1)));

        then(sharedGroupOrderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("상품 가격을 찾을 수 없으면 주문을 만들지 않는다")
    void productNotFound() {
        given(sharedGroupOrderRepository.existsBySharedGroupPurchaseId(PURCHASE_ID)).willReturn(false);
        given(orderProductClient.findAllByInventoryIds(anyCollection())).willReturn(List.of());

        assertThatThrownBy(() -> createUseCase.createFromClosedPurchase(
                event(List.of(1L), new SharedGroupPurchaseClosedEvent.Item(2L, 4L, 1))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_PRODUCT_NOT_FOUND.getMsg());
        then(sharedGroupOrderRepository).should(never()).save(any());
    }

    private SharedGroupPurchaseClosedEvent event(List<Long> participants, SharedGroupPurchaseClosedEvent.Item... items) {
        return new SharedGroupPurchaseClosedEvent(PURCHASE_ID, CREATOR, participants, List.of(items), CLOSED_AT);
    }
}
