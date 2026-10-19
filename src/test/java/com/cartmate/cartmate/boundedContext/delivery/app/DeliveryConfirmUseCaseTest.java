package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
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

class DeliveryConfirmUseCaseTest {

    private static final long BUYER = 1L;
    private static final long SELLER_ID = 10L;
    private static final long OTHER_MEMBER = 2L;

    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private final DeliveryConfirmedEventPublisher eventPublisher = mock(DeliveryConfirmedEventPublisher.class);
    private DeliveryConfirmUseCase useCase;
    private Delivery delivery;

    @BeforeEach
    void setUp() {
        useCase = new DeliveryConfirmUseCase(deliveryRepository, eventPublisher);

        delivery = Delivery.create(BUYER, SELLER_ID, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");
        ReflectionTestUtils.setField(delivery, "id", 5L);
        delivery.prepare("CJ대한통운", "658219384910");
        delivery.ship();
        delivery.deliver();
        when(deliveryRepository.findById(5L)).thenReturn(Optional.of(delivery));
    }

    @Test
    @DisplayName("수령인 본인이 배송완료된 배송을 구매확정하면 구매확정이 되고 변경된 값을 응답한다")
    void confirm() {
        DeliveryResponse response = useCase.confirm(5L, BUYER);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CONFIRMED);
        assertThat(delivery.getConfirmedAt()).isNotNull();
        assertThat(response.deliveryId()).isEqualTo(5L);
        assertThat(response.status()).isEqualTo(DeliveryStatus.CONFIRMED);
        verify(eventPublisher).publish(delivery);
    }

    @Test
    @DisplayName("이미 구매확정된 배송을 다시 확정해도 성공한다")
    void confirmIsIdempotent() {
        useCase.confirm(5L, BUYER);

        DeliveryResponse response = useCase.confirm(5L, BUYER);

        assertThat(response.status()).isEqualTo(DeliveryStatus.CONFIRMED);
        verify(eventPublisher, times(1)).publish(delivery);
    }

    @Test
    @DisplayName("수령인이 아닌 회원은 구매확정할 수 없다")
    void otherMemberCannotConfirm() {
        assertThatThrownBy(() -> useCase.confirm(5L, OTHER_MEMBER))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("403-DELIVERY_ACCESS_DENIED");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getConfirmedAt()).isNull();
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("판매자는 구매확정할 수 없다")
    void sellerCannotConfirm() {
        assertThatThrownBy(() -> useCase.confirm(5L, SELLER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("403-DELIVERY_ACCESS_DENIED");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    @DisplayName("배송완료 전에는 구매확정할 수 없어 409 오류다")
    void cannotConfirmBeforeDelivered() {
        ReflectionTestUtils.setField(delivery, "status", DeliveryStatus.SHIPPING);

        assertThatThrownBy(() -> useCase.confirm(5L, BUYER))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("409-DELIVERY_INVALID_STATUS");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SHIPPING);
    }

    @Test
    @DisplayName("없는 배송은 404 오류다")
    void notFound() {
        when(deliveryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.confirm(99L, BUYER))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("404-DELIVERY_NOT_FOUND");
    }

    @Test
    @DisplayName("회원 ID가 없으면 400 오류다")
    void memberIdRequired() {
        assertThatThrownBy(() -> useCase.confirm(5L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("400-DELIVERY_MEMBER_ID_REQUIRED");
    }
}
