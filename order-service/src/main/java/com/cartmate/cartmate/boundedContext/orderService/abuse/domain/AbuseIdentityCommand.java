package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

public record AbuseIdentityCommand(
        Long memberId,
        String receiverPhone,
        String zipCode,
        String address,
        String addressDetail
) {
}
