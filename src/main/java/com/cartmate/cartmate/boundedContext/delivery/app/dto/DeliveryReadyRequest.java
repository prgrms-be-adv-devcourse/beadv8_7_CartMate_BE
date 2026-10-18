package com.cartmate.cartmate.boundedContext.delivery.app.dto;

// 배송 준비 처리 요청. 판매자가 입력한 택배사와 송장번호다.
public record DeliveryReadyRequest(
        String carrier,
        String trackingNumber
) {
}
