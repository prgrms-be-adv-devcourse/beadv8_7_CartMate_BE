package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryCancelUseCaseTest {

    private static final long ORDER_ID = 100L;

    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private DeliveryCancelUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeliveryCancelUseCase(deliveryRepository);
    }

    private Delivery newDelivery(long sellerId, String key) {
        return Delivery.create(1L, sellerId, ORDER_ID, null, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", key, "hash");
    }

    @Test
    @DisplayName("주문의 모든 배송이 배송요청 상태면 전부 취소된다")
    void cancelByOrder() {
        Delivery first = newDelivery(10L, "key-1");
        Delivery second = newDelivery(11L, "key-2");
        when(deliveryRepository.findAllByOrderIdAndPurchaseTypeForUpdate(ORDER_ID, PurchaseType.INDIVIDUAL_GROUP))
                .thenReturn(List.of(first, second));

        useCase.cancelByOrder(ORDER_ID);

        assertThat(first.getStatus()).isEqualTo(DeliveryStatus.CANCELED);
        assertThat(second.getStatus()).isEqualTo(DeliveryStatus.CANCELED);
    }

    @Test
    @DisplayName("배송 하나라도 판매자가 확인한 뒤라면 예외가 나고 확인된 배송은 그대로다")
    void cancelByOrderFailsWhenAnyPrepared() {
        Delivery requested = newDelivery(10L, "key-1");
        Delivery prepared = newDelivery(11L, "key-2");
        prepared.prepare("CJ대한통운", "658219384910");
        when(deliveryRepository.findAllByOrderIdAndPurchaseTypeForUpdate(ORDER_ID, PurchaseType.INDIVIDUAL_GROUP))
                .thenReturn(List.of(requested, prepared));

        assertThatThrownBy(() -> useCase.cancelByOrder(ORDER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("DELIVERY_NOT_CANCELABLE");
        assertThat(prepared.getStatus()).isEqualTo(DeliveryStatus.PREPARING);
    }

    @Test
    @DisplayName("배송이 없는 주문이면 아무 일도 일어나지 않는다")
    void cancelByOrderWithoutDelivery() {
        when(deliveryRepository.findAllByOrderIdAndPurchaseTypeForUpdate(ORDER_ID, PurchaseType.INDIVIDUAL_GROUP))
                .thenReturn(List.of());

        useCase.cancelByOrder(ORDER_ID);
    }
}
