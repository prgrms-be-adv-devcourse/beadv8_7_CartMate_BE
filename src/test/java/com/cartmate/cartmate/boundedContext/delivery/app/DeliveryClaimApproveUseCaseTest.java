package com.cartmate.cartmate.boundedContext.delivery.app;

import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.SellerClaimResponse;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimReason;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.delivery.domain.Delivery;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaimItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryItem;
import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimItemRepository;
import com.cartmate.cartmate.boundedContext.delivery.out.DeliveryClaimRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.member.SellerQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryClaimApproveUseCaseTest {

    private static final long BUYER = 1L;
    private static final long SELLER_MEMBER = 20L;
    private static final long SELLER_ID = 10L;

    private final DeliveryClaimRepository claimRepository = mock(DeliveryClaimRepository.class);
    private final DeliveryClaimItemRepository claimItemRepository = mock(DeliveryClaimItemRepository.class);
    // isSellerOwner는 default 메서드라 실제 로직을 호출하도록 한다.
    private final SellerQuery sellerQuery = mock(SellerQuery.class, CALLS_REAL_METHODS);
    private DeliveryClaimApproveUseCase useCase;
    private DeliveryClaim claim;

    @BeforeEach
    void setUp() {
        useCase = new DeliveryClaimApproveUseCase(claimRepository, claimItemRepository, sellerQuery);

        Delivery delivery = Delivery.create(BUYER, SELLER_ID, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");
        ReflectionTestUtils.setField(delivery, "id", 5L);
        claim = DeliveryClaim.create(delivery, BUYER, ClaimType.EXCHANGE, ClaimReason.SIZE_MISMATCH, "L로 교환");
        ReflectionTestUtils.setField(claim, "id", 7L);
        when(claimRepository.findById(7L)).thenReturn(Optional.of(claim));

        DeliveryItem deliveryItem = DeliveryItem.create(delivery, 1000L, "티셔츠", 10_000L, 2L);
        ReflectionTestUtils.setField(deliveryItem, "id", 50L);
        when(claimItemRepository.findAllWithDeliveryItemByClaimIds(List.of(7L)))
                .thenReturn(List.of(DeliveryClaimItem.create(claim, deliveryItem, 1L)));

        doReturn(Optional.of(SELLER_ID)).when(sellerQuery).findSellerIdByMemberId(SELLER_MEMBER);
        doReturn(Optional.of(99L)).when(sellerQuery).findSellerIdByMemberId(40L);
    }

    @Test
    @DisplayName("해당 배송의 판매자가 수락하면 수락 상태가 되고 주문번호와 상품을 포함해 응답한다")
    void approve() {
        SellerClaimResponse response = useCase.approve(7L, SELLER_MEMBER);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getProcessedAt()).isNotNull();
        assertThat(response.claimId()).isEqualTo(7L);
        assertThat(response.orderId()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(response.items())
                .extracting(DeliveryClaimResponse.Item::deliveryItemId, DeliveryClaimResponse.Item::productName)
                .containsExactly(tuple(50L, "티셔츠"));
    }

    @Test
    @DisplayName("이미 수락된 클레임을 다시 수락해도 성공한다")
    void approveIsIdempotent() {
        useCase.approve(7L, SELLER_MEMBER);

        assertThat(useCase.approve(7L, SELLER_MEMBER).status()).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    @DisplayName("구매자는 수락할 수 없다")
    void buyerCannotApprove() {
        assertThatThrownBy(() -> useCase.approve(7L, BUYER))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("403-DELIVERY_ACCESS_DENIED");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REQUESTED);
    }

    @Test
    @DisplayName("다른 판매자는 수락할 수 없다")
    void otherSellerCannotApprove() {
        assertThatThrownBy(() -> useCase.approve(7L, 40L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("403-DELIVERY_ACCESS_DENIED");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REQUESTED);
    }

    @Test
    @DisplayName("거절됐거나 완료된 클레임은 409 오류이고 상태가 바뀌지 않는다")
    void invalidStatus() {
        ReflectionTestUtils.setField(claim, "status", ClaimStatus.REJECTED);

        assertThatThrownBy(() -> useCase.approve(7L, SELLER_MEMBER))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("409-DELIVERY_CLAIM_INVALID_STATUS");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
    }

    @Test
    @DisplayName("없는 클레임은 404 오류다")
    void notFound() {
        when(claimRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.approve(99L, SELLER_MEMBER))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("404-DELIVERY_CLAIM_NOT_FOUND");
    }

    @Test
    @DisplayName("회원 ID가 없으면 400 오류다")
    void memberIdRequired() {
        assertThatThrownBy(() -> useCase.approve(7L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("400-DELIVERY_MEMBER_ID_REQUIRED");
    }
}
