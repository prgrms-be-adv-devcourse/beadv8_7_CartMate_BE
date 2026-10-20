package com.cartmate.cartmate.boundedContext.wallet.out;

import lombok.Getter;

@Getter
public class ChargeGatewayException extends RuntimeException {

    public enum FailureType {
        REJECTED,          // 승인 거절이 확정되어 충전 주문을 실패 처리할 수 있음
        ALREADY_PROCESSED, // 이미 처리된 결제이므로 조회로 결과 확인
        UNKNOWN            // 처리 여부가 불명확하므로 실패로 확정하지 않고 조회
    }

    private final FailureType failureType;
    private final String code; // 결제사가 준 오류 코드. 응답을 받지 못했으면 null

    public ChargeGatewayException(FailureType failureType, String code, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
        this.code = code;
    }

    public ChargeGatewayException(FailureType failureType, String code, String message) {
        this(failureType, code, message, null);
    }
}
