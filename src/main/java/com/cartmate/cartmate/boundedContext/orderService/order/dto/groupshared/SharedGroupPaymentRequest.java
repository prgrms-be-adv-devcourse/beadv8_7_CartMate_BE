package com.cartmate.cartmate.boundedContext.orderService.order.dto.groupshared;

import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;

// 분담금 결제 요청. 만든 사람(개설자)은 팀 배송지를 함께 보낸다.
public record SharedGroupPaymentRequest(
        TeamDeliveryAddress teamDeliveryAddress
) {
}