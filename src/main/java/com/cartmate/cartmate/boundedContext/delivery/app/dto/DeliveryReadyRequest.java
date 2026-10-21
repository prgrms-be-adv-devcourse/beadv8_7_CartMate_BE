package com.cartmate.cartmate.boundedContext.delivery.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 배송 준비 처리 요청. 판매자가 입력한 택배사와 송장번호다.
public record DeliveryReadyRequest(
        @NotBlank
        @Size(max = 50)
        String carrier,

        @NotBlank
        @Size(max = 50)
        String trackingNumber
) {
}
