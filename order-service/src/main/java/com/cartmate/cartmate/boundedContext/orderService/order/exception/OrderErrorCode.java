package com.cartmate.cartmate.boundedContext.orderService.order.exception;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    // 조회
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", "존재하지 않는 주문입니다.", HttpStatus.NOT_FOUND),

    // 공동 주문 생성
    ORDER_PRODUCT_NOT_FOUND("ORDER_PRODUCT_NOT_FOUND", "주문할 상품 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ORDER_SHARED_CREATION_EXPIRED("ORDER_SHARED_CREATION_EXPIRED", "마감한 지 오래되어 공동 주문을 만들 수 없습니다.", HttpStatus.CONFLICT),
    ORDER_NO_ITEMS("ORDER_NO_ITEMS", "주문할 상품이 없습니다.", HttpStatus.BAD_REQUEST),
    ORDER_CREATOR_NOT_IN_PARTICIPANTS("ORDER_CREATOR_NOT_IN_PARTICIPANTS", "생성자는 참여자에 포함되어야 합니다.", HttpStatus.BAD_REQUEST),
    ORDER_DUPLICATE_PARTICIPANTS("ORDER_DUPLICATE_PARTICIPANTS", "참여자가 중복되었습니다.", HttpStatus.BAD_REQUEST),
    ORDER_INVALID_QUANTITY("ORDER_INVALID_QUANTITY", "수량은 1개 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    ORDER_INVALID_PRICE("ORDER_INVALID_PRICE", "가격은 0원 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    ORDER_INVALID_SHARE_AMOUNT("ORDER_INVALID_SHARE_AMOUNT", "부담 금액은 0원 이상이어야 합니다.", HttpStatus.BAD_REQUEST),

    // 참여자·권한
    ORDER_NOT_PARTICIPANT("ORDER_NOT_PARTICIPANT", "공동 주문 참여자가 아닙니다.", HttpStatus.FORBIDDEN),
    ORDER_CREATOR_ONLY("ORDER_CREATOR_ONLY", "공동 주문 생성자만 할 수 있습니다.", HttpStatus.FORBIDDEN),

    // 결제
    ORDER_NOT_PAYABLE("ORDER_NOT_PAYABLE", "결제할 수 없는 주문입니다.", HttpStatus.CONFLICT),
    ORDER_NOT_PAYING("ORDER_NOT_PAYING", "결제 중인 공동 주문이 아닙니다.", HttpStatus.CONFLICT),
    ORDER_STOCK_NOT_RESERVED("ORDER_STOCK_NOT_RESERVED", "재고를 선점하지 않은 공동 주문입니다.", HttpStatus.CONFLICT),
    ORDER_PAYMENT_ID_REQUIRED("ORDER_PAYMENT_ID_REQUIRED", "결제 ID가 필요합니다.", HttpStatus.BAD_REQUEST),
    ORDER_ALREADY_PAID("ORDER_ALREADY_PAID", "이미 결제한 참여자입니다.", HttpStatus.CONFLICT),
    ORDER_TEAM_ADDRESS_REQUIRED("ORDER_TEAM_ADDRESS_REQUIRED", "생성자는 팀 배송지를 입력해야 합니다.", HttpStatus.BAD_REQUEST),
    ORDER_INVALID_DELIVERY_ADDRESS("ORDER_INVALID_DELIVERY_ADDRESS", "받는 사람, 연락처, 우편번호, 주소는 모두 입력해야 합니다.", HttpStatus.BAD_REQUEST),

    // 개인 주문
    ORDER_PRODUCT_NOT_PURCHASABLE("ORDER_PRODUCT_NOT_PURCHASABLE", "구매할 수 없는 상품입니다.", HttpStatus.CONFLICT),
    ORDER_PAYMENT_RELEASE_PENDING("ORDER_PAYMENT_RELEASE_PENDING", "결제 정리가 아직 끝나지 않아 지금은 취소할 수 없습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.SERVICE_UNAVAILABLE),
    ORDER_NOT_PAYMENT_PENDING("ORDER_NOT_PAYMENT_PENDING", "결제 대기 중인 주문이 아닙니다.", HttpStatus.CONFLICT),
    ORDER_PAYMENT_EXPIRED("ORDER_PAYMENT_EXPIRED", "결제 기한이 지난 주문입니다.", HttpStatus.CONFLICT),
    ORDER_BUYER_ONLY("ORDER_BUYER_ONLY", "주문한 회원만 할 수 있습니다.", HttpStatus.FORBIDDEN),
    ORDER_ALREADY_CANCELED("ORDER_ALREADY_CANCELED", "이미 취소된 주문입니다.", HttpStatus.CONFLICT),
    ORDER_ALREADY_PENDING("ORDER_ALREADY_PENDING", "결제 대기 중인 주문이 이미 있습니다.", HttpStatus.CONFLICT),
    ORDER_DUPLICATE_ITEM("ORDER_DUPLICATE_ITEM", "같은 상품이 중복으로 담겼습니다.", HttpStatus.BAD_REQUEST),
    ORDER_NUMBER_GENERATION_FAILED("ORDER_NUMBER_GENERATION_FAILED", "주문 번호를 생성하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    // 개인물품 공동구매 주문 (order 쪽 공동구매 사본으로 판단, cart의 같은 뜻 코드와 상태 코드를 맞춘다)
    ORDER_GROUP_PURCHASE_NOT_FOUND("ORDER_GROUP_PURCHASE_NOT_FOUND", "존재하지 않는 공동구매입니다.", HttpStatus.NOT_FOUND),
    ORDER_GROUP_PURCHASE_SALE_CLOSED("ORDER_GROUP_PURCHASE_SALE_CLOSED", "판매가 마감된 공동구매입니다.", HttpStatus.CONFLICT),
    ORDER_GROUP_PURCHASE_PRODUCT_NOT_SOLD("ORDER_GROUP_PURCHASE_PRODUCT_NOT_SOLD", "이 공동구매에서 판매하지 않는 상품입니다.", HttpStatus.BAD_REQUEST),
    ORDER_GROUP_PURCHASE_PAYMENT_CLOSED("ORDER_GROUP_PURCHASE_PAYMENT_CLOSED", "판매가 끝난 공동구매이거나 더 이상 판매하지 않는 상품이 있어 결제할 수 없습니다.", HttpStatus.CONFLICT);

    private final String code;
    private final String msg;
    private final HttpStatus status;
}
