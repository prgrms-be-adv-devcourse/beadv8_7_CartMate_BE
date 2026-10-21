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
        Delivery delivery = Delivery.create(1L, 10L, 100L, null, PurchaseType.INDIVIDUAL_GROUP,
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
        Delivery delivery = Delivery.create(1L, 10L, 100L, null, PurchaseType.INDIVIDUAL_GROUP,
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
    @DisplayName("수락된 클레임을 완료 처리하면 완료 상태가 되고 완료 시각이 기록되며 수락 시각은 유지된다")
    void complete() {
        DeliveryClaim claim = newClaim();
        claim.approve();
        LocalDateTime approvedAt = claim.getProcessedAt();

        claim.complete();

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.COMPLETED);
        assertThat(claim.getCompletedAt()).isNotNull();
        assertThat(claim.getProcessedAt()).isEqualTo(approvedAt);
    }

    @Test
    @DisplayName("이미 완료된 클레임을 다시 완료 처리해도 성공하고 완료 시각이 바뀌지 않는다")
    void completeIsIdempotent() {
        DeliveryClaim claim = newClaim();
        claim.approve();
        claim.complete();
        LocalDateTime firstCompletedAt = claim.getCompletedAt();

        claim.complete();

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.COMPLETED);
        assertThat(claim.getCompletedAt()).isEqualTo(firstCompletedAt);
    }

    @Test
    @DisplayName("수락 전이거나 거절된 클레임은 완료 처리할 수 없다")
    void completeInvalidStatus() {
        for (ClaimStatus status : new ClaimStatus[]{ClaimStatus.REQUESTED, ClaimStatus.REJECTED}) {
            DeliveryClaim claim = newClaim();
            ReflectionTestUtils.setField(claim, "status", status);

            assertThatThrownBy(claim::complete)
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("409-DELIVERY_CLAIM_INVALID_STATUS");
            assertThat(claim.getStatus()).isEqualTo(status);
            assertThat(claim.getCompletedAt()).isNull();
        }
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
