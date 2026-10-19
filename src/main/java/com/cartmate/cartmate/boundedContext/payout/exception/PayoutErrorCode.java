package com.cartmate.cartmate.boundedContext.payout.exception;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PayoutErrorCode implements ErrorCode {

    PAYOUT_REQUIRED_VALUE_MISSING("PAYOUT_REQUIRED_VALUE_MISSING", "정산 생성에 필요한 값이 없습니다.", HttpStatus.BAD_REQUEST),
    PAYOUT_INVALID_AMOUNT("PAYOUT_INVALID_AMOUNT", "판매 금액은 0 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    PAYOUT_NOT_PENDING("PAYOUT_NOT_PENDING", "대기 중인 정산만 처리할 수 있습니다.", HttpStatus.CONFLICT);

    private final String code;
    private final String msg;
    private final HttpStatus status;
}
