package com.cartmate.cartmate.boundedContext.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// 관리자 수동 환불
public record PaymentRefundRequest(
        @NotNull(message = "환불 금액을 입력해주세요.")
        @Positive(message = "환불 금액은 0보다 커야 합니다.")
        Long amount,

        @Size(max = 300)
        String memo
) {
}