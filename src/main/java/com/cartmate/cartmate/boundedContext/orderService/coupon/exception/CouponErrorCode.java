package com.cartmate.cartmate.boundedContext.orderService.coupon.exception;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CouponErrorCode implements ErrorCode {

    COUPON_INVALID("COUPON_INVALID", "쿠폰 설정이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    COUPON_NOT_FOUND("COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    COUPON_NOT_DOWNLOADABLE("COUPON_NOT_DOWNLOADABLE", "지금 받을 수 없는 쿠폰입니다.", HttpStatus.CONFLICT),
    COUPON_SOLD_OUT("COUPON_SOLD_OUT", "쿠폰이 모두 소진되었습니다.", HttpStatus.CONFLICT),
    COUPON_ALREADY_ISSUED("COUPON_ALREADY_ISSUED", "이미 받은 쿠폰입니다.", HttpStatus.CONFLICT),
    COUPON_NOT_AVAILABLE("COUPON_NOT_AVAILABLE", "사용할 수 없는 쿠폰입니다.", HttpStatus.CONFLICT),
    COUPON_EXPIRED("COUPON_EXPIRED", "사용 기간이 지난 쿠폰입니다.", HttpStatus.CONFLICT);

    private final String code;
    private final String msg;
    private final HttpStatus status;
}