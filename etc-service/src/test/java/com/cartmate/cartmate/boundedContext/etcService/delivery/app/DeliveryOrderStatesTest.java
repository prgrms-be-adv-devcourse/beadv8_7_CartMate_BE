package com.cartmate.cartmate.boundedContext.etcService.delivery.app;

import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryOrderState;
import com.cartmate.cartmate.boundedContext.etcService.delivery.out.DeliveryOrderStateRepository;
import com.cartmate.cartmate.boundedContext.etcService.delivery.out.DeliverySellerCancelRepository;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryOrderStatesTest {

    private static final long ORDER_ID = 100L;
    private static final long SELLER_ID = 1007L;

    private final DeliveryOrderStateRepository stateRepository = mock(DeliveryOrderStateRepository.class);
    private final DeliverySellerCancelRepository sellerCancelRepository = mock(DeliverySellerCancelRepository.class);
    private final DeliveryOrderState state = mock(DeliveryOrderState.class);
    private final DeliveryOrderStates states = new DeliveryOrderStates(stateRepository, sellerCancelRepository);

    private void givenOrderState(boolean canceled) {
        when(stateRepository.insertIfAbsent(anyLong(), anyString(), anyString(), any())).thenReturn(0);
        when(stateRepository.findForUpdate(ORDER_ID, PurchaseType.INDIVIDUAL_GROUP)).thenReturn(Optional.of(state));
        when(state.isCanceled()).thenReturn(canceled);
    }

    @Test
    @DisplayName("판매글 내림으로 그 판매자 몫이 취소된 메모가 있으면 그 판매자 배송을 만들지 않는다")
    void rejectsCreationOfCanceledSeller() {
        givenOrderState(false);
        when(sellerCancelRepository.existsByOrderIdAndPurchaseTypeAndSellerId(
                ORDER_ID, PurchaseType.INDIVIDUAL_GROUP, SELLER_ID)).thenReturn(true);

        assertThat(states.acceptCreation(ORDER_ID, PurchaseType.INDIVIDUAL_GROUP, SELLER_ID)).isFalse();
    }

    @Test
    @DisplayName("주문도 판매자 몫도 취소되지 않았으면 배송을 만든다")
    void acceptsCreation() {
        givenOrderState(false);

        assertThat(states.acceptCreation(ORDER_ID, PurchaseType.INDIVIDUAL_GROUP, SELLER_ID)).isTrue();
    }

    @Test
    @DisplayName("주문 전체가 취소됐으면 판매자 메모를 보지 않고 배송을 만들지 않는다")
    void rejectsCreationOfCanceledOrder() {
        givenOrderState(true);

        assertThat(states.acceptCreation(ORDER_ID, PurchaseType.INDIVIDUAL_GROUP, SELLER_ID)).isFalse();
    }
}
