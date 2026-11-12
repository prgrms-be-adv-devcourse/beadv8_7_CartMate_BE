package com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto;

import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import jakarta.validation.constraints.NotNull;

// 판매자 사유 취소 요청. 정해진 사유 중 하나를 고른다. 자유 입력 메모는 받지 않는다.
public record DeliverySellerCancelRequest(
        @NotNull
        DeliveryCancelReason cancelReason
) {
}
