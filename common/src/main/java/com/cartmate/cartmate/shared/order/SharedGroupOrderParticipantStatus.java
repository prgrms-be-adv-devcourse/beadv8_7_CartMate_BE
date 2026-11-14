package com.cartmate.cartmate.shared.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SharedGroupOrderParticipantStatus {
    UNPAID("미결제"),
    PAID("결제 완료");

    private final String label;
}
