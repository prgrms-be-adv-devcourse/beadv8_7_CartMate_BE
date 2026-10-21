package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryAutoConfirmUseCaseTest {

    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private final DeliveryClaimRepository deliveryClaimRepository = mock(DeliveryClaimRepository.class);
    private final DeliveryConfirmedEventPublisher eventPublisher = mock(DeliveryConfirmedEventPublisher.class);
    private DeliveryAutoConfirmUseCase useCase;
    private Delivery delivery;

    @BeforeEach
    void setUp() {
        useCase = new DeliveryAutoConfirmUseCase(deliveryRepository, deliveryClaimRepository, eventPublisher);

        delivery = Delivery.create(1L, 10L, 100L, null, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");
        delivery.prepare("CJ대한통운", "658219384910");
        delivery.ship();
        delivery.deliver();
        when(deliveryRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(delivery));
    }

    @Test
    @DisplayName("배송완료된 배송을 구매확정으로 바꾼다")
    void confirm() {
        useCase.confirm(5L);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CONFIRMED);
        assertThat(delivery.getConfirmedAt()).isNotNull();
        verify(eventPublisher).publish(delivery);
    }

    @Test
    @DisplayName("조회 후 구매자가 먼저 확정한 건은 그대로 두고 성공한다")
    void alreadyConfirmedByBuyer() {
        delivery.confirm();
        var confirmedAt = delivery.getConfirmedAt();

        useCase.confirm(5L);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CONFIRMED);
        assertThat(delivery.getConfirmedAt()).isEqualTo(confirmedAt);
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("배송완료가 아닌 상태는 409 오류이고 상태가 바뀌지 않는다")
    void invalidStatus() {
        ReflectionTestUtils.setField(delivery, "status", DeliveryStatus.SHIPPING);

        assertThatThrownBy(() -> useCase.confirm(5L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("409-DELIVERY_INVALID_STATUS");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SHIPPING);
    }

    @Test
    @DisplayName("없는 배송은 404 오류다")
    void notFound() {
        when(deliveryRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.confirm(99L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("404-DELIVERY_NOT_FOUND");
    }

    @Test
    @DisplayName("처리 중인 교환·환불 요청이 있으면 확정하지 않고 건너뛰며 이벤트도 발행하지 않는다")
    void skipWhenActiveClaim() {
        when(deliveryClaimRepository.hasActiveClaim(any())).thenReturn(true);

        useCase.confirm(5L);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getConfirmedAt()).isNull();
        verify(eventPublisher, never()).publish(any());
    }
}
