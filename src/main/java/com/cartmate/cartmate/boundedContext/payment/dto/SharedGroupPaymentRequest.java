package com.cartmate.cartmate.boundedContext.payment.dto;

import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;

// 개설자는 팀 배송지를 함께 보내야 한다. 참여자는 비워도 됨
public record SharedGroupPaymentRequest(
        TeamDeliveryAddress teamDeliveryAddress
) {
}