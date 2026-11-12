package com.cartmate.cartmate.boundedContext.etcService.delivery.domain;

import com.cartmate.cartmate.boundedContext.etcService.delivery.exception.DeliveryErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BuyerCancelVerdict {
    ALLOWED("취소 가능", null),
    NOT_CANCELABLE("취소 불가", DeliveryErrorCode.DELIVERY_NOT_CANCELABLE),
    SHIPPED_AFTER_REQUEST("요청 후 발송됨", DeliveryErrorCode.DELIVERY_CANCEL_DELAYED);

    private final String label;
    private final DeliveryErrorCode rejectCode;
}
