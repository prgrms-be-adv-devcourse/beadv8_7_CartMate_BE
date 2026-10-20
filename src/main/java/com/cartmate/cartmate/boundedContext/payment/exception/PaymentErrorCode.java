package com.cartmate.cartmate.boundedContext.payment.exception;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_INVALID_AMOUNT("PAYMENT_INVALID_AMOUNT", "결제 금액이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_ORDER_NOT_PAYABLE("PAYMENT_ORDER_NOT_PAYABLE", "결제할 수 없는 주문 상태입니다.", HttpStatus.CONFLICT),
    PAYMENT_IN_PROGRESS("PAYMENT_IN_PROGRESS", "이미 처리 중인 결제입니다. 잠시 후 다시 확인해주세요.", HttpStatus.CONFLICT),
    PAYMENT_NOT_FOUND("PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PAYMENT_ALREADY_CANCELED("PAYMENT_ALREADY_CANCELED", "이미 취소된 결제입니다.", HttpStatus.CONFLICT),
    PAYMENT_REFUND_INVALID_AMOUNT("PAYMENT_REFUND_INVALID_AMOUNT", "환불 금액이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_REFUND_EXCEEDS_REMAINING("PAYMENT_REFUND_EXCEEDS_REMAINING", "환불 가능한 금액을 초과했습니다.", HttpStatus.CONFLICT),
    PAYMENT_REFUND_NOT_ALLOWED("PAYMENT_REFUND_NOT_ALLOWED", "공동물품 구매는 확정 후 환불할 수 없습니다.", HttpStatus.CONFLICT),
    PAYMENT_REFUND_IN_PROGRESS("PAYMENT_REFUND_IN_PROGRESS", "이미 처리 중인 환불입니다.", HttpStatus.CONFLICT);


    private final String code;
    private final String msg;
    private final HttpStatus status;
}