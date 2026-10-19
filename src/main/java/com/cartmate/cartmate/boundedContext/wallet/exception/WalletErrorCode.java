package com.cartmate.cartmate.boundedContext.wallet.exception;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WalletErrorCode implements ErrorCode {

    // 요청 입력값
    WALLET_INVALID_AMOUNT("WALLET_INVALID_AMOUNT", "금액은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
    WALLET_MEMBER_ID_REQUIRED("WALLET_MEMBER_ID_REQUIRED", "회원 ID는 필수입니다.", HttpStatus.BAD_REQUEST),
    WALLET_ID_REQUIRED("WALLET_ID_REQUIRED", "지갑 ID는 필수입니다.", HttpStatus.BAD_REQUEST),
    WALLET_ORDER_ID_REQUIRED("WALLET_ORDER_ID_REQUIRED", "주문 ID는 필수입니다.", HttpStatus.BAD_REQUEST),
    WALLET_REFUND_ID_REQUIRED("WALLET_REFUND_ID_REQUIRED", "환불 ID는 필수입니다.", HttpStatus.BAD_REQUEST),
    WALLET_PAYMENT_ID_REQUIRED("WALLET_PAYMENT_ID_REQUIRED", "결제 ID는 필수입니다.", HttpStatus.BAD_REQUEST),
    WALLET_SETTLEMENT_ID_REQUIRED("WALLET_SETTLEMENT_ID_REQUIRED", "정산 ID는 필수입니다.", HttpStatus.BAD_REQUEST),
    WALLET_ORDER_IDS_REQUIRED("WALLET_ORDER_IDS_REQUIRED", "정산할 주문 ID 목록은 비어 있을 수 없습니다.", HttpStatus.BAD_REQUEST),
    WALLET_ORDER_IDS_CONTAIN_NULL("WALLET_ORDER_IDS_CONTAIN_NULL", "주문 ID 목록에 null을 포함할 수 없습니다.", HttpStatus.BAD_REQUEST),
    WALLET_IDEMPOTENCY_KEY_REQUIRED("WALLET_IDEMPOTENCY_KEY_REQUIRED", "멱등성 키는 필수입니다.", HttpStatus.BAD_REQUEST),
    WALLET_PAYMENT_KEY_REQUIRED("WALLET_PAYMENT_KEY_REQUIRED", "결제 키는 필수입니다.", HttpStatus.BAD_REQUEST),

    // 지갑 조회·접근·잔액
    WALLET_NOT_FOUND("WALLET_NOT_FOUND", "지갑을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    WALLET_ACCESS_DENIED("WALLET_ACCESS_DENIED", "해당 지갑에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN),
    WALLET_ALREADY_EXISTS("WALLET_ALREADY_EXISTS", "이미 지갑이 존재합니다.", HttpStatus.CONFLICT),
    WALLET_INSUFFICIENT_BALANCE("WALLET_INSUFFICIENT_BALANCE", "지갑 잔액이 부족합니다.", HttpStatus.CONFLICT),

    // 충전 주문 조회·금액·상태
    WALLET_CHARGE_ORDER_NOT_FOUND("WALLET_CHARGE_ORDER_NOT_FOUND", "충전 주문을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    WALLET_CHARGE_AMOUNT_MISMATCH("WALLET_CHARGE_AMOUNT_MISMATCH", "요청 금액이 충전 주문 금액과 일치하지 않습니다.", HttpStatus.CONFLICT),
    WALLET_PAYMENT_KEY_MISMATCH("WALLET_PAYMENT_KEY_MISMATCH", "결제 키가 기존 충전의 결제 키와 일치하지 않습니다.", HttpStatus.CONFLICT),
    WALLET_CHARGE_NOT_COMPLETABLE("WALLET_CHARGE_NOT_COMPLETABLE", "현재 상태에서는 충전을 완료할 수 없습니다.", HttpStatus.CONFLICT),
    WALLET_CHARGE_NOT_CANCELABLE("WALLET_CHARGE_NOT_CANCELABLE", "현재 상태에서는 충전 주문을 취소할 수 없습니다.", HttpStatus.CONFLICT),
    WALLET_CHARGE_NOT_FAILABLE("WALLET_CHARGE_NOT_FAILABLE", "현재 상태에서는 충전 주문을 실패 처리할 수 없습니다.", HttpStatus.CONFLICT),

    // 출금 요청 조회·상태
    WALLET_WITHDRAW_ORDER_ID_REQUIRED("WALLET_WITHDRAW_ORDER_ID_REQUIRED", "출금 요청 ID는 필수입니다.", HttpStatus.BAD_REQUEST),
    WALLET_WITHDRAW_ORDER_NOT_FOUND("WALLET_WITHDRAW_ORDER_NOT_FOUND", "출금 요청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    WALLET_WITHDRAW_NOT_COMPLETABLE("WALLET_WITHDRAW_NOT_COMPLETABLE", "현재 상태에서는 출금을 완료할 수 없습니다.", HttpStatus.CONFLICT),
    WALLET_WITHDRAW_NOT_CANCELABLE("WALLET_WITHDRAW_NOT_CANCELABLE", "현재 상태에서는 출금 요청을 취소할 수 없습니다.", HttpStatus.CONFLICT),
    WALLET_WITHDRAW_NOT_FAILABLE("WALLET_WITHDRAW_NOT_FAILABLE", "현재 상태에서는 출금 요청을 실패 처리할 수 없습니다.", HttpStatus.CONFLICT),

    // 기존 요청·거래와의 충돌
    WALLET_IDEMPOTENCY_CONFLICT("WALLET_IDEMPOTENCY_CONFLICT", "동일한 멱등성 키에 다른 요청 내용을 사용할 수 없습니다.", HttpStatus.CONFLICT),
    WALLET_TRANSACTION_AMOUNT_MISMATCH("WALLET_TRANSACTION_AMOUNT_MISMATCH", "요청 금액이 기존 거래 금액과 일치하지 않습니다.", HttpStatus.CONFLICT),

    // 홀딩 조회·환불·정산
    WALLET_HOLDING_NOT_FOUND("WALLET_HOLDING_NOT_FOUND", "홀딩 내역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    WALLET_HOLDING_AMOUNT_MISMATCH("WALLET_HOLDING_AMOUNT_MISMATCH", "환불 금액이 홀딩 금액과 일치하지 않습니다.", HttpStatus.CONFLICT),
    WALLET_HOLDING_REFUND_EXCEEDED("WALLET_HOLDING_REFUND_EXCEEDED", "환불 금액이 홀딩 잔여 금액을 초과합니다.", HttpStatus.CONFLICT),
    WALLET_HOLDING_ALREADY_CANCELED("WALLET_HOLDING_ALREADY_CANCELED", "이미 취소된 홀딩입니다.", HttpStatus.CONFLICT),
    WALLET_HOLDING_NOT_CANCELABLE("WALLET_HOLDING_NOT_CANCELABLE", "현재 상태에서는 홀딩을 취소할 수 없습니다.", HttpStatus.CONFLICT),
    WALLET_HOLDING_ALREADY_SETTLED("WALLET_HOLDING_ALREADY_SETTLED", "이미 정산된 홀딩입니다.", HttpStatus.CONFLICT),
    WALLET_HOLDING_NOT_SETTLEABLE("WALLET_HOLDING_NOT_SETTLEABLE", "현재 상태에서는 홀딩을 정산할 수 없습니다.", HttpStatus.CONFLICT),
    WALLET_SETTLEMENT_CONFLICT("WALLET_SETTLEMENT_CONFLICT", "다른 정산으로 이미 처리된 홀딩입니다.", HttpStatus.CONFLICT),
    WALLET_SETTLEMENT_HOLDING_MISSING("WALLET_SETTLEMENT_HOLDING_MISSING", "요청한 주문 중 정산 가능한 홀딩이 없는 주문이 있습니다.", HttpStatus.CONFLICT),

    // 내부 처리 불변식
    WALLET_INTERNAL_WALLET_MISSING("WALLET_INTERNAL_WALLET_MISSING", "거래 처리에 필요한 지갑 정보가 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    WALLET_INTERNAL_TRANSACTION_TYPE_MISSING("WALLET_INTERNAL_TRANSACTION_TYPE_MISSING", "거래 처리에 필요한 거래 유형이 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    WALLET_INVALID_BALANCE_AFTER("WALLET_INVALID_BALANCE_AFTER", "거래 후 잔액은 0 이상이어야 합니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String msg;
    private final HttpStatus status;

}
