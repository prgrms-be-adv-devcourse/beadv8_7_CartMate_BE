package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimReason;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryClaimCreateUseCaseTest {

    private static final long BUYER = 1L;
    private static final long SELLER_ID = 10L;
    private static final long OTHER_MEMBER = 2L;

    private final DeliveryRepository deliveryRepository = mock(DeliveryRepository.class);
    private final DeliveryClaimRepository claimRepository = mock(DeliveryClaimRepository.class);
    private DeliveryClaimCreateUseCase useCase;
    private Delivery delivery;

    @BeforeEach
    void setUp() {
        useCase = new DeliveryClaimCreateUseCase(deliveryRepository, claimRepository);

        delivery = Delivery.create(BUYER, SELLER_ID, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");
        ReflectionTestUtils.setField(delivery, "id", 5L);
        delivery.prepare("CJ대한통운", "658219384910");
        delivery.ship();
        delivery.deliver();
        when(deliveryRepository.findById(5L)).thenReturn(Optional.of(delivery));
    }

    @Test
    @DisplayName("수령인 본인이 배송완료된 배송에 접수하면 접수 상태의 클레임이 저장된다")
    void create() {
        DeliveryClaimResponse response = useCase.create(
                5L, BUYER, ClaimType.EXCHANGE, ClaimReason.SIZE_MISMATCH, "L로 교환 원합니다");

        ArgumentCaptor<DeliveryClaim> captor = ArgumentCaptor.forClass(DeliveryClaim.class);
        verify(claimRepository).save(captor.capture());
        DeliveryClaim saved = captor.getValue();
        assertThat(saved.getDelivery()).isSameAs(delivery);
        assertThat(saved.getMemberId()).isEqualTo(BUYER);
        assertThat(saved.getStatus()).isEqualTo(ClaimStatus.REQUESTED);

        assertThat(response.deliveryId()).isEqualTo(5L);
        assertThat(response.claimType()).isEqualTo(ClaimType.EXCHANGE);
        assertThat(response.reason()).isEqualTo(ClaimReason.SIZE_MISMATCH);
        assertThat(response.detail()).isEqualTo("L로 교환 원합니다");
        assertThat(response.status()).isEqualTo(ClaimStatus.REQUESTED);
    }

    @Test
    @DisplayName("상세 사유는 앞뒤 공백을 제거하고 비어 있으면 null로 저장한다")
    void detailIsNormalized() {
        assertThat(useCase.create(5L, BUYER, ClaimType.REFUND, ClaimReason.OTHER, "  불량입니다  ").detail())
                .isEqualTo("불량입니다");
        assertThat(useCase.create(5L, BUYER, ClaimType.REFUND, ClaimReason.OTHER, "   ").detail()).isNull();
        assertThat(useCase.create(5L, BUYER, ClaimType.REFUND, ClaimReason.OTHER, null).detail()).isNull();
    }

    @Test
    @DisplayName("수령인이 아닌 회원과 판매자는 접수할 수 없다")
    void onlyReceiverCanCreate() {
        assertThatThrownBy(() -> useCase.create(5L, OTHER_MEMBER, ClaimType.REFUND, ClaimReason.OTHER, null))
                .isInstanceOf(ServiceException.class).hasMessageContaining("403-DELIVERY_ACCESS_DENIED");
        assertThatThrownBy(() -> useCase.create(5L, SELLER_ID, ClaimType.REFUND, ClaimReason.OTHER, null))
                .isInstanceOf(ServiceException.class).hasMessageContaining("403-DELIVERY_ACCESS_DENIED");
        verify(claimRepository, never()).save(any());
    }

    @Test
    @DisplayName("배송완료가 아닌 상태에서는 접수할 수 없고 구매확정 이후에도 접수할 수 없다")
    void onlyDeliveredCanBeClaimed() {
        for (DeliveryStatus status : new DeliveryStatus[]{
                DeliveryStatus.REQUESTED, DeliveryStatus.PREPARING, DeliveryStatus.SHIPPING,
                DeliveryStatus.CONFIRMED, DeliveryStatus.CANCELED}) {
            ReflectionTestUtils.setField(delivery, "status", status);

            assertThatThrownBy(() -> useCase.create(5L, BUYER, ClaimType.REFUND, ClaimReason.OTHER, null))
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("409-DELIVERY_INVALID_STATUS");
        }
        verify(claimRepository, never()).save(any());
    }

    @Test
    @DisplayName("처리 중인 클레임이 이미 있으면 409 오류다")
    void duplicateActiveClaim() {
        when(claimRepository.existsByDeliveryIdAndStatusIn(eq(5L), any())).thenReturn(true);

        assertThatThrownBy(() -> useCase.create(5L, BUYER, ClaimType.REFUND, ClaimReason.OTHER, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("409-DELIVERY_CLAIM_ALREADY_EXISTS");
        verify(claimRepository, never()).save(any());
    }

    @Test
    @DisplayName("접수와 승인 상태만 처리 중으로 보고 거절·완료된 클레임은 새 접수를 막지 않는다")
    void onlyRequestedAndApprovedAreActive() {
        useCase.create(5L, BUYER, ClaimType.REFUND, ClaimReason.OTHER, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<ClaimStatus>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(claimRepository).existsByDeliveryIdAndStatusIn(eq(5L), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(ClaimStatus.REQUESTED, ClaimStatus.APPROVED);
    }

    @Test
    @DisplayName("입력값이 잘못되면 400 오류이고 저장하지 않는다")
    void invalidInputs() {
        assertThatThrownBy(() -> useCase.create(5L, null, ClaimType.REFUND, ClaimReason.OTHER, null))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_MEMBER_ID_REQUIRED");
        assertThatThrownBy(() -> useCase.create(5L, BUYER, null, ClaimReason.OTHER, null))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_CLAIM_TYPE_REQUIRED");
        assertThatThrownBy(() -> useCase.create(5L, BUYER, ClaimType.REFUND, null, null))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_CLAIM_REASON_REQUIRED");
        assertThatThrownBy(() -> useCase.create(5L, BUYER, ClaimType.REFUND, ClaimReason.OTHER, "가".repeat(1001)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("400-DELIVERY_CLAIM_DETAIL_TOO_LONG");
        verify(claimRepository, never()).save(any());
    }

    @Test
    @DisplayName("상세 사유가 최대 길이이면 접수된다")
    void detailAtMaxLength() {
        DeliveryClaimResponse response = useCase.create(
                5L, BUYER, ClaimType.REFUND, ClaimReason.OTHER, "가".repeat(1000));

        assertThat(response.detail()).hasSize(1000);
    }

    @Test
    @DisplayName("없는 배송은 404 오류다")
    void notFound() {
        when(deliveryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.create(99L, BUYER, ClaimType.REFUND, ClaimReason.OTHER, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("404-DELIVERY_NOT_FOUND");
    }
}
