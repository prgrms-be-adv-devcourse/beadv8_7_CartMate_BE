package com.cartmate.cartmate.boundedContext.abuse.exception;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AbuseErrorCode implements ErrorCode {

    ABUSE_INVALID_THRESHOLD("ABUSE_INVALID_THRESHOLD", "어뷰징 탐지 기준값이 올바르지 않습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    ABUSE_INVALID_SIGNAL("ABUSE_INVALID_SIGNAL", "어뷰징 신호 정보가 올바르지 않습니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String msg;
    private final HttpStatus status;
}
