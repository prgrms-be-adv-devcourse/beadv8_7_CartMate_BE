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

class DeliveryReadyUseCaseTest {

    private static final long BUYER = 1L;
    private static final long SELLER_MEMBER = 20L;
    private static final long SELLER_ID = 10L;

    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    // isSellerOwner는 default 메서드라 실제 로직을 호출하도록 한다.
    private final SellerQuery sellerQuery = mock(SellerQuery.class, CALLS_REAL_METHODS);
    private DeliveryReadyUseCase useCase;
    private Delivery delivery;

    @BeforeEach
    void setUp() {
        useCase = new DeliveryReadyUseCase(deliveryRepository, sellerQuery);

        delivery = Delivery.create(BUYER, SELLER_ID, 100L, null, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");
        ReflectionTestUtils.setField(delivery, "id", 5L);
        when(deliveryRepository.findById(5L)).thenReturn(Optional.of(delivery));

        doReturn(Optional.of(SELLER_ID)).when(sellerQuery).findSellerIdByMemberId(SELLER_MEMBER);
        doReturn(Optional.of(99L)).when(sellerQuery).findSellerIdByMemberId(40L);
    }

    @Test
    @DisplayName("해당 배송의 판매자가 송장을 등록하면 배송준비 상태로 바뀌고 변경된 값을 응답한다")
    void ready() {
        DeliveryResponse response = useCase.ready(5L, SELLER_MEMBER, "CJ대한통운", "658219384910");

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PREPARING);
        assertThat(response.deliveryId()).isEqualTo(5L);
        assertThat(response.status()).isEqualTo(DeliveryStatus.PREPARING);
        assertThat(response.carrier()).isEqualTo("CJ대한통운");
        assertThat(response.trackingNumber()).isEqualTo("658219384910");
    }

    @Test
    @DisplayName("구매자는 준비 처리할 수 없다")
    void buyerCannotReady() {
        assertThatThrownBy(() -> useCase.ready(5L, BUYER, "CJ대한통운", "658219384910"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("403-DELIVERY_ACCESS_DENIED");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.REQUESTED);
    }

    @Test
    @DisplayName("다른 판매자는 준비 처리할 수 없다")
    void otherSellerCannotReady() {
        assertThatThrownBy(() -> useCase.ready(5L, 40L, "CJ대한통운", "658219384910"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("403-DELIVERY_ACCESS_DENIED");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.REQUESTED);
    }

    @Test
    @DisplayName("없는 배송은 404 오류다")
    void notFound() {
        when(deliveryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.ready(99L, SELLER_MEMBER, "CJ대한통운", "658219384910"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("404-DELIVERY_NOT_FOUND");
    }

    @Test
    @DisplayName("회원 ID가 없으면 400 오류다")
    void memberIdRequired() {
        assertThatThrownBy(() -> useCase.ready(5L, null, "CJ대한통운", "658219384910"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("400-DELIVERY_MEMBER_ID_REQUIRED");
    }

    @Test
    @DisplayName("택배사나 송장번호가 비어 있으면 400 오류이고 상태가 바뀌지 않는다")
    void invalidCarrier() {
        assertThatThrownBy(() -> useCase.ready(5L, SELLER_MEMBER, "", "658219384910"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("400-DELIVERY_CARRIER_REQUIRED");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.REQUESTED);
    }
}
