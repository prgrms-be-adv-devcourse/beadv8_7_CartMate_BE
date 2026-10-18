package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.member.SellerQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryShipUseCaseTest {

    private static final long BUYER = 1L;
    private static final long SELLER_MEMBER = 20L;
    private static final long SELLER_ID = 10L;

    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    // isSellerOwner는 default 메서드라 실제 로직을 호출하도록 한다.
    private final SellerQuery sellerQuery = mock(SellerQuery.class, CALLS_REAL_METHODS);
    private DeliveryShipUseCase useCase;
    private Delivery delivery;

    @BeforeEach
    void setUp() {
        useCase = new DeliveryShipUseCase(deliveryRepository, sellerQuery);

        delivery = Delivery.create(BUYER, SELLER_ID, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");
        ReflectionTestUtils.setField(delivery, "id", 5L);
        delivery.prepare("CJ대한통운", "658219384910");
        when(deliveryRepository.findById(5L)).thenReturn(Optional.of(delivery));

        doReturn(Optional.of(SELLER_ID)).when(sellerQuery).findSellerIdByMemberId(SELLER_MEMBER);
        doReturn(Optional.of(99L)).when(sellerQuery).findSellerIdByMemberId(40L);
    }

    @Test
    @DisplayName("해당 배송의 판매자가 출발 처리하면 배송중이 되고 변경된 값을 응답한다")
    void ship() {
        DeliveryResponse response = useCase.ship(5L, SELLER_MEMBER);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SHIPPING);
        assertThat(response.deliveryId()).isEqualTo(5L);
        assertThat(response.status()).isEqualTo(DeliveryStatus.SHIPPING);
        assertThat(response.carrier()).isEqualTo("CJ대한통운");
        assertThat(response.trackingNumber()).isEqualTo("658219384910");
    }

    @Test
    @DisplayName("구매자는 출발 처리할 수 없다")
    void buyerCannotShip() {
        assertThatThrownBy(() -> useCase.ship(5L, BUYER))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("403-DELIVERY_ACCESS_DENIED");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PREPARING);
    }

    @Test
    @DisplayName("다른 판매자는 출발 처리할 수 없다")
    void otherSellerCannotShip() {
        assertThatThrownBy(() -> useCase.ship(5L, 40L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("403-DELIVERY_ACCESS_DENIED");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PREPARING);
    }

    @Test
    @DisplayName("없는 배송은 404 오류다")
    void notFound() {
        when(deliveryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.ship(99L, SELLER_MEMBER))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("404-DELIVERY_NOT_FOUND");
    }

    @Test
    @DisplayName("회원 ID가 없으면 400 오류다")
    void memberIdRequired() {
        assertThatThrownBy(() -> useCase.ship(5L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("400-DELIVERY_MEMBER_ID_REQUIRED");
    }

    @Test
    @DisplayName("배송요청 상태에서는 출발 처리할 수 없어 409 오류다")
    void cannotShipBeforePrepared() {
        ReflectionTestUtils.setField(delivery, "status", DeliveryStatus.REQUESTED);

        assertThatThrownBy(() -> useCase.ship(5L, SELLER_MEMBER))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("409-DELIVERY_INVALID_STATUS");
    }
}
