package com.cartmate.cartmate.boundedContext.payment.exception;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_INVALID_AMOUNT("PAYMENT_INVALID_AMOUNT", "결제 금액이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_ORDER_NOT_FOUND("PAYMENT_ORDER_NOT_FOUND", "결제할 주문을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PAYMENT_ORDER_ACCESS_DENIED("PAYMENT_ORDER_ACCESS_DENIED", "본인의 주문만 결제할 수 있습니다.", HttpStatus.FORBIDDEN),
    PAYMENT_ORDER_NOT_PAYABLE("PAYMENT_ORDER_NOT_PAYABLE", "결제할 수 없는 주문 상태입니다.", HttpStatus.CONFLICT),
    PAYMENT_IN_PROGRESS("PAYMENT_IN_PROGRESS", "이미 처리 중인 결제입니다. 잠시 후 다시 확인해주세요.", HttpStatus.CONFLICT),
    PAYMENT_NOT_FOUND("PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PAYMENT_ACCESS_DENIED("PAYMENT_ACCESS_DENIED", "본인의 결제만 조회·취소할 수 있습니다.", HttpStatus.FORBIDDEN),
    PAYMENT_ALREADY_CANCELED("PAYMENT_ALREADY_CANCELED", "이미 취소된 결제입니다.", HttpStatus.CONFLICT);

    private final String code;
    private final String msg;
    private final HttpStatus status;
}