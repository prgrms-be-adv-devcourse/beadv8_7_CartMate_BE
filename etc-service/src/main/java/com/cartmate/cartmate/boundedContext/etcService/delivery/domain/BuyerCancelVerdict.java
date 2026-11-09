package com.cartmate.cartmate.boundedContext.etcService.delivery.domain;

import com.cartmate.cartmate.boundedContext.etcService.delivery.exception.DeliveryErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BuyerCancelVerdict {
    ALLOWED(null),
    NOT_CANCELABLE(DeliveryErrorCode.DELIVERY_NOT_CANCELABLE),
    SHIPPED_AFTER_REQUEST(DeliveryErrorCode.DELIVERY_CANCEL_DELAYED);

    private final DeliveryErrorCode rejectCode;
}
