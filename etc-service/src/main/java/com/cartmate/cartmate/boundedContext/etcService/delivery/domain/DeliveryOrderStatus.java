package com.cartmate.cartmate.boundedContext.etcService.delivery.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DeliveryOrderStatus {
    CREATED("생성"),
    CANCELED("취소");

    private final String label;
}
