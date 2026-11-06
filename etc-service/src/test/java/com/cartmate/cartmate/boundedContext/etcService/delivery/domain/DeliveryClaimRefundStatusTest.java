package com.cartmate.cartmate.boundedContext.etcService.delivery.domain;

import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryClaimRefundStatusTest {

    private DeliveryClaim claim(ClaimType type) {
        return DeliveryClaim.create(null, 1L, type, ClaimReason.PRODUCT_DEFECT, null);
    }

    @Test
    @DisplayName("환불이 필요한 승인은 환불중이 되고, 환불이 필요 없는 승인은 환불 불필요로 남는다")
    void approveSetsRefundStatus() {
        DeliveryClaim refund = claim(ClaimType.REFUND);
        refund.approve(true);
        DeliveryClaim exchange = claim(ClaimType.EXCHANGE);
        exchange.approve(false);

        assertThat(refund.getRefundStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(exchange.getRefundStatus()).isEqualTo(RefundStatus.NONE);
    }

    @Test
    @DisplayName("환불중이거나 환불에 실패한 클레임은 처리 완료할 수 없고, 환불이 끝나면 처리 완료할 수 있다")
    void completeRequiresRefundCompleted() {
        DeliveryClaim claim = claim(ClaimType.REFUND);
        claim.approve(true);

        assertThatThrownBy(claim::complete).isInstanceOf(ServiceException.class)
                .hasMessageContaining("DELIVERY_CLAIM_REFUND_PENDING");

        claim.failRefund();
        assertThatThrownBy(claim::complete).isInstanceOf(ServiceException.class)
                .hasMessageContaining("DELIVERY_CLAIM_REFUND_FAILED");

        claim.completeRefund();
        claim.complete();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.COMPLETED);
    }

    @Test
    @DisplayName("같은 결과를 두 번 받아도 그대로이고, 환불 완료 뒤에 늦게 온 실패는 완료를 되돌리지 못한다")
    void refundResultIsIdempotentAndOrderSafe() {
        DeliveryClaim claim = claim(ClaimType.REFUND);
        claim.approve(true);

        claim.completeRefund();
        claim.completeRefund();
        claim.failRefund();

        assertThat(claim.getRefundStatus()).isEqualTo(RefundStatus.COMPLETED);
    }

    @Test
    @DisplayName("환불에 실패한 뒤 재시도로 환불되면 환불 완료로 바뀐다")
    void failedRefundCanBecomeCompleted() {
        DeliveryClaim claim = claim(ClaimType.REFUND);
        claim.approve(true);

        claim.failRefund();
        claim.completeRefund();

        assertThat(claim.getRefundStatus()).isEqualTo(RefundStatus.COMPLETED);
    }

    @Test
    @DisplayName("환불이 필요 없는 클레임에 환불 결과가 오면 거부한다")
    void refundResultRejectedWhenNotRequired() {
        DeliveryClaim claim = claim(ClaimType.EXCHANGE);
        claim.approve(false);

        assertThatThrownBy(claim::completeRefund).isInstanceOf(ServiceException.class)
                .hasMessageContaining("DELIVERY_CLAIM_REFUND_NOT_REQUIRED");
        assertThatThrownBy(claim::failRefund).isInstanceOf(ServiceException.class)
                .hasMessageContaining("DELIVERY_CLAIM_REFUND_NOT_REQUIRED");
    }

    @Test
    @DisplayName("이미 승인된 클레임을 다시 승인해도 환불 상태는 바뀌지 않는다")
    void reapproveKeepsRefundStatus() {
        DeliveryClaim claim = claim(ClaimType.REFUND);
        claim.approve(true);
        claim.completeRefund();

        claim.approve(true);

        assertThat(claim.getRefundStatus()).isEqualTo(RefundStatus.COMPLETED);
    }
}
