package com.cartmate.cartmate.boundedContext.delivery.exception;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DeliveryErrorCode implements ErrorCode {

    // 요청 입력값
    DELIVERY_CREATE_COMMAND_REQUIRED("DELIVERY_CREATE_COMMAND_REQUIRED", "배송 생성 정보가 필요합니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_MEMBER_ID_REQUIRED("DELIVERY_MEMBER_ID_REQUIRED", "회원 ID가 필요합니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_SELLER_ID_REQUIRED("DELIVERY_SELLER_ID_REQUIRED", "판매자 ID가 필요합니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_ORDER_ID_REQUIRED("DELIVERY_ORDER_ID_REQUIRED", "주문 ID가 필요합니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_PURCHASE_TYPE_REQUIRED("DELIVERY_PURCHASE_TYPE_REQUIRED", "배송 유형이 필요합니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_RECEIVER_NAME_REQUIRED("DELIVERY_RECEIVER_NAME_REQUIRED", "수령인 이름이 필요합니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_RECEIVER_PHONE_REQUIRED("DELIVERY_RECEIVER_PHONE_REQUIRED", "수령인 연락처가 필요합니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_ZIP_CODE_REQUIRED("DELIVERY_ZIP_CODE_REQUIRED", "우편번호가 필요합니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_ADDRESS_REQUIRED("DELIVERY_ADDRESS_REQUIRED", "기본주소가 필요합니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_ADDRESS_DETAIL_REQUIRED("DELIVERY_ADDRESS_DETAIL_REQUIRED", "상세주소는 null일 수 없습니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_ITEMS_REQUIRED("DELIVERY_ITEMS_REQUIRED", "배송 상품 목록이 필요합니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_ITEMS_CONTAIN_NULL("DELIVERY_ITEMS_CONTAIN_NULL", "배송 상품 목록에 null을 포함할 수 없습니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_ORDER_ITEM_ID_REQUIRED("DELIVERY_ORDER_ITEM_ID_REQUIRED", "주문상품 ID가 필요합니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_INVALID_QUANTITY("DELIVERY_INVALID_QUANTITY", "배송 수량은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_IDEMPOTENCY_KEY_REQUIRED("DELIVERY_IDEMPOTENCY_KEY_REQUIRED", "멱등키가 필요합니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_OPTION_CONTAIN_NULL("DELIVERY_OPTION_CONTAIN_NULL", "배송 상품 옵션 목록에 null을 포함할 수 없습니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_OPTION_NAME_REQUIRED("DELIVERY_OPTION_NAME_REQUIRED", "옵션명이 필요합니다.", HttpStatus.BAD_REQUEST),
    DELIVERY_OPTION_VALUE_REQUIRED("DELIVERY_OPTION_VALUE_REQUIRED", "옵션값이 필요합니다.", HttpStatus.BAD_REQUEST),

    // 조회·접근
    DELIVERY_NOT_FOUND("DELIVERY_NOT_FOUND", "배송을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    DELIVERY_ACCESS_DENIED("DELIVERY_ACCESS_DENIED", "배송 조회 권한이 없습니다.", HttpStatus.FORBIDDEN),
    DELIVERY_SELLER_ONLY("DELIVERY_SELLER_ONLY", "판매자만 조회할 수 있습니다.", HttpStatus.FORBIDDEN),

    // 기존 요청과의 충돌
    DELIVERY_IDEMPOTENCY_CONFLICT("DELIVERY_IDEMPOTENCY_CONFLICT", "동일한 멱등키에 다른 배송 요청이 전달되었습니다.", HttpStatus.CONFLICT);

    private final String code;
    private final String msg;
    private final HttpStatus status;

}
