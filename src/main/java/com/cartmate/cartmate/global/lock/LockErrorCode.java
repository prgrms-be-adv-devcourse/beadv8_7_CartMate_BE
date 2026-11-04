package com.cartmate.cartmate.global.lock;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LockErrorCode implements ErrorCode {

    LOCK_ACQUISITION_FAILED("LOCK_ACQUISITION_FAILED", "같은 요청을 처리 중입니다. 잠시 후 다시 시도해 주세요.", HttpStatus.CONFLICT);

    private final String code;
    private final String msg;
    private final HttpStatus status;
}
