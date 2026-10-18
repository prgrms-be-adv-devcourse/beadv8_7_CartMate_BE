package com.cartmate.cartmate.boundedContext.delivery.domain;

import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryClaimTest {

    @Test
    @DisplayName("클레임을 생성하면 접수 상태로 시작하고 입력한 값이 저장된다")
    void createStartsAsRequested() {
        Delivery delivery = Delivery.create(1L, 10L, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");

        DeliveryClaim claim = DeliveryClaim.create(
                delivery, 1L, ClaimType.EXCHANGE, ClaimReason.SIZE_MISMATCH, "L로 교환 원합니다");

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REQUESTED);
        assertThat(claim.getDelivery()).isSameAs(delivery);
        assertThat(claim.getMemberId()).isEqualTo(1L);
        assertThat(claim.getClaimType()).isEqualTo(ClaimType.EXCHANGE);
        assertThat(claim.getReason()).isEqualTo(ClaimReason.SIZE_MISMATCH);
        assertThat(claim.getDetail()).isEqualTo("L로 교환 원합니다");
        assertThat(claim.getProcessedAt()).isNull();
    }

    private DeliveryClaim newClaim() {
        Delivery delivery = Delivery.create(1L, 10L, 100L, PurchaseType.INDIVIDUAL_GROUP,
                "홍길동", "010-0000-0000", "12345", "서울", "101호", "key-1", "hash");
        return DeliveryClaim.create(delivery, 1L, ClaimType.REFUND, ClaimReason.PRODUCT_DEFECT, "불량입니다");
    }

    @Test
    @DisplayName("접수된 클레임을 수락하면 수락 상태가 되고 처리 시각이 기록된다")
    void approve() {
        DeliveryClaim claim = newClaim();

        claim.approve();

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 수락된 클레임을 다시 수락해도 성공하고 처리 시각이 바뀌지 않는다")
    void approveIsIdempotent() {
        DeliveryClaim claim = newClaim();
        claim.approve();
        LocalDateTime firstProcessedAt = claim.getProcessedAt();

        claim.approve();

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getProcessedAt()).isEqualTo(firstProcessedAt);
    }

    @Test
    @DisplayName("거절됐거나 완료된 클레임은 수락할 수 없다")
    void approveInvalidStatus() {
        for (ClaimStatus status : new ClaimStatus[]{ClaimStatus.REJECTED, ClaimStatus.COMPLETED}) {
            DeliveryClaim claim = newClaim();
            ReflectionTestUtils.setField(claim, "status", status);

            assertThatThrownBy(claim::approve)
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("409-DELIVERY_CLAIM_INVALID_STATUS");
            assertThat(claim.getStatus()).isEqualTo(status);
            assertThat(claim.getProcessedAt()).isNull();
        }
    }
}
