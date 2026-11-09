package com.cartmate.cartmate.boundedContext.trust.exception;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TrustErrorCode implements ErrorCode {

    // 내부 처리 불변식 (이벤트 수신·점수 계산 과정에서 발생하며 사용자 요청값이 아니다)
    TRUST_SELLER_ID_REQUIRED("TRUST_SELLER_ID_REQUIRED", "판매자 ID는 필수입니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    TRUST_HISTORY_TYPE_REQUIRED("TRUST_HISTORY_TYPE_REQUIRED", "거래 이력 유형은 필수입니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    TRUST_SOURCE_KEY_REQUIRED("TRUST_SOURCE_KEY_REQUIRED", "이력 식별 키는 필수입니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    TRUST_OCCURRED_AT_REQUIRED("TRUST_OCCURRED_AT_REQUIRED", "이력 발생 시각은 필수입니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    TRUST_INVALID_METRICS("TRUST_INVALID_METRICS", "신뢰도 집계값이 올바르지 않습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String msg;
    private final HttpStatus status;

}
