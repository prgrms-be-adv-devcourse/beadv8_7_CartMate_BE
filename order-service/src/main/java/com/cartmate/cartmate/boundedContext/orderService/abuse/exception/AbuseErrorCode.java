package com.cartmate.cartmate.boundedContext.orderService.abuse.exception;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AbuseErrorCode implements ErrorCode {

    ABUSE_INVALID_THRESHOLD("ABUSE_INVALID_THRESHOLD", "어뷰징 탐지 기준값이 올바르지 않습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    ABUSE_INVALID_SIGNAL("ABUSE_INVALID_SIGNAL", "어뷰징 신호 정보가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    ABUSE_MEMBER_STATE_NOT_FOUND("ABUSE_MEMBER_STATE_NOT_FOUND", "어뷰징 회원 상태를 찾을 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    ABUSE_INVALID_RESTRICTION_HOURS("ABUSE_INVALID_RESTRICTION_HOURS", "수동 제한 시간은 1 ~ 720시간입니다.", HttpStatus.BAD_REQUEST),
    ABUSE_ADMIN_REASON_REQUIRED("ABUSE_ADMIN_REASON_REQUIRED", "처리 사유를 1 ~ 500자로 입력해야 합니다.", HttpStatus.BAD_REQUEST),
    ABUSE_NO_FAILED_SIGNAL("ABUSE_NO_FAILED_SIGNAL", "다시 판정할 실패 신호가 없습니다.", HttpStatus.CONFLICT),
    ABUSE_INVALID_LEVEL("ABUSE_INVALID_LEVEL", "어뷰징 단계 값이 올바르지 않습니다. NONE · CAUTION · SUSPICIOUS · RESTRICTED 중 하나입니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String msg;
    private final HttpStatus status;
}
