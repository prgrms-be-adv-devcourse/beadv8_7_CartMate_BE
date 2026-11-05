package com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto;

import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.DeliveryClaim;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 교환·환불 거절 요청. 판매자가 거절 사유를 보낸다.
public record DeliveryClaimRejectRequest(
        @NotBlank
        @Size(max = DeliveryClaim.DETAIL_MAX_LENGTH)
        String reason
) {}
