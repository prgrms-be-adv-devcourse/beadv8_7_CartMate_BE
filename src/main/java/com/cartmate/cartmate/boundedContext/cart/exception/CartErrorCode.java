package com.cartmate.cartmate.boundedContext.cart.exception;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CartErrorCode implements ErrorCode {

    // 상품
    CART_PRODUCT_OPTION_NOT_FOUND("CART_PRODUCT_OPTION_NOT_FOUND", "존재하지 않는 상품 옵션입니다.", HttpStatus.NOT_FOUND),
    CART_PRODUCT_NOT_FOUND("CART_PRODUCT_NOT_FOUND", "존재하지 않는 상품입니다.", HttpStatus.NOT_FOUND),
    CART_PRODUCT_NOT_PURCHASABLE("CART_PRODUCT_NOT_PURCHASABLE", "구매할 수 없는 상품입니다.", HttpStatus.CONFLICT),
    CART_PRODUCT_NOT_ON_SALE("CART_PRODUCT_NOT_ON_SALE", "판매 중이 아닌 상품입니다.", HttpStatus.CONFLICT),
    CART_INVALID_QUANTITY("CART_INVALID_QUANTITY", "수량은 1개 이상 999개 이하여야 합니다.", HttpStatus.BAD_REQUEST),
    CART_INVALID_QUANTITY_DELTA("CART_INVALID_QUANTITY_DELTA", "증감 수량은 0이 아닌 -998 이상 998 이하여야 합니다.", HttpStatus.BAD_REQUEST),

    // 개인 장바구니
    CART_NOT_FOUND("CART_NOT_FOUND", "장바구니가 존재하지 않습니다.", HttpStatus.NOT_FOUND),
    CART_ITEM_NOT_FOUND("CART_ITEM_NOT_FOUND", "장바구니에 없는 상품입니다.", HttpStatus.NOT_FOUND),
    CART_ORDER_ITEMS_REQUIRED("CART_ORDER_ITEMS_REQUIRED", "주문할 장바구니 상품을 하나 이상 선택해야 합니다.", HttpStatus.BAD_REQUEST),

    // 공동구매 공통
    CART_GROUP_PURCHASE_NOT_FOUND("CART_GROUP_PURCHASE_NOT_FOUND", "존재하지 않는 공동구매입니다.", HttpStatus.NOT_FOUND),
    CART_INVALID_INVITE_CODE("CART_INVALID_INVITE_CODE", "유효하지 않은 초대 코드입니다.", HttpStatus.NOT_FOUND),
    CART_INVITE_CODE_GENERATION_FAILED("CART_INVITE_CODE_GENERATION_FAILED", "초대 코드를 생성하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    CART_CREATOR_ONLY("CART_CREATOR_ONLY", "공동구매 생성자만 할 수 있습니다.", HttpStatus.FORBIDDEN),
    CART_CREATOR_ONLY_VIEW("CART_CREATOR_ONLY_VIEW", "공동구매 생성자만 볼 수 있습니다.", HttpStatus.FORBIDDEN),
    CART_INVALID_TITLE("CART_INVALID_TITLE", "제목은 1자 이상 50자 이하여야 합니다.", HttpStatus.BAD_REQUEST),

    // 공동구매-공동물품: 참여
    CART_SHARED_NOT_PARTICIPANT("CART_SHARED_NOT_PARTICIPANT", "공동구매 참여자가 아닙니다.", HttpStatus.FORBIDDEN),
    CART_SHARED_PARTICIPANT_ONLY_VIEW("CART_SHARED_PARTICIPANT_ONLY_VIEW", "공동구매 참여자만 볼 수 있습니다.", HttpStatus.FORBIDDEN),
    CART_SHARED_MEMBER_NOT_FOUND("CART_SHARED_MEMBER_NOT_FOUND", "참여하지 않은 회원입니다.", HttpStatus.NOT_FOUND),
    CART_SHARED_ALREADY_JOINED("CART_SHARED_ALREADY_JOINED", "이미 참여한 공동구매입니다.", HttpStatus.CONFLICT),
    CART_SHARED_FULL("CART_SHARED_FULL", "참여 인원이 가득 찼습니다.", HttpStatus.CONFLICT),
    CART_SHARED_CREATOR_CANNOT_LEAVE("CART_SHARED_CREATOR_CANNOT_LEAVE", "생성자는 공동구매를 나갈 수 없습니다.", HttpStatus.BAD_REQUEST),
    CART_SHARED_CREATOR_CANNOT_BE_KICKED("CART_SHARED_CREATOR_CANNOT_BE_KICKED", "생성자는 내보낼 수 없습니다.", HttpStatus.BAD_REQUEST),
    CART_SHARED_INVALID_MAX_PARTICIPANTS("CART_SHARED_INVALID_MAX_PARTICIPANTS", "참여 인원은 2명 이상 100명 이하여야 합니다.", HttpStatus.BAD_REQUEST),
    CART_SHARED_MAX_PARTICIPANTS_DECREASE("CART_SHARED_MAX_PARTICIPANTS_DECREASE", "최대 인원은 늘리는 것만 가능합니다.", HttpStatus.BAD_REQUEST),

    // 공동구매-공동물품: 품목
    CART_SHARED_ITEM_NOT_FOUND("CART_SHARED_ITEM_NOT_FOUND", "공동구매에 없는 상품입니다.", HttpStatus.NOT_FOUND),
    CART_SHARED_ITEM_OPTION_MISMATCH("CART_SHARED_ITEM_OPTION_MISMATCH", "같은 상품의 옵션으로만 변경할 수 있습니다.", HttpStatus.BAD_REQUEST),
    CART_SHARED_EDIT_CLOSED("CART_SHARED_EDIT_CLOSED", "수정이 마감된 공동구매입니다.", HttpStatus.CONFLICT),

    // 공동구매-공동물품: 마감·취소·복제
    CART_SHARED_NOT_CLOSABLE("CART_SHARED_NOT_CLOSABLE", "마감할 수 없는 상태입니다.", HttpStatus.CONFLICT),
    CART_SHARED_EMPTY_ITEMS("CART_SHARED_EMPTY_ITEMS", "상품이 없는 공동구매는 마감할 수 없습니다.", HttpStatus.CONFLICT),
    CART_SHARED_HAS_UNAVAILABLE_ITEMS("CART_SHARED_HAS_UNAVAILABLE_ITEMS", "구매할 수 없는 상품이 있어 마감할 수 없습니다.", HttpStatus.CONFLICT),
    CART_SHARED_NOT_CANCELABLE("CART_SHARED_NOT_CANCELABLE", "취소할 수 없는 상태입니다.", HttpStatus.CONFLICT),
    CART_SHARED_NOT_AUTO_CLOSE_TARGET("CART_SHARED_NOT_AUTO_CLOSE_TARGET", "자동 마감 대상이 아닙니다.", HttpStatus.CONFLICT),
    CART_SHARED_NOT_FROZEN_RELEASE_TARGET("CART_SHARED_NOT_FROZEN_RELEASE_TARGET", "마감 보류 해제 대상이 아닙니다.", HttpStatus.CONFLICT),
    CART_SHARED_NOT_DUPLICABLE("CART_SHARED_NOT_DUPLICABLE", "마감되거나 취소된 공동구매만 다시 만들 수 있습니다.", HttpStatus.CONFLICT),

    // 공동구매-개인물품
    CART_INDIVIDUAL_PRODUCTS_REQUIRED("CART_INDIVIDUAL_PRODUCTS_REQUIRED", "판매할 상품을 하나 이상 선택해야 합니다.", HttpStatus.BAD_REQUEST),
    CART_INDIVIDUAL_PRODUCT_ALREADY_ON_SALE("CART_INDIVIDUAL_PRODUCT_ALREADY_ON_SALE", "이미 판매 중인 상품입니다.", HttpStatus.CONFLICT),
    CART_INDIVIDUAL_PRODUCT_NOT_ON_SALE("CART_INDIVIDUAL_PRODUCT_NOT_ON_SALE", "판매 중인 상품이 아닙니다.", HttpStatus.NOT_FOUND),
    CART_INDIVIDUAL_PRODUCT_NOT_SOLD("CART_INDIVIDUAL_PRODUCT_NOT_SOLD", "이 공동구매에서 판매하지 않는 상품입니다.", HttpStatus.BAD_REQUEST),
    CART_INDIVIDUAL_PRODUCT_HAS_ORDERS("CART_INDIVIDUAL_PRODUCT_HAS_ORDERS", "이미 구매한 사람이 있는 상품은 뺄 수 없습니다.", HttpStatus.CONFLICT),
    CART_INDIVIDUAL_LAST_PRODUCT("CART_INDIVIDUAL_LAST_PRODUCT", "판매 상품은 하나 이상 있어야 합니다. 판매를 멈추려면 마감해 주세요.", HttpStatus.CONFLICT),
    CART_INDIVIDUAL_DEADLINE_IN_PAST("CART_INDIVIDUAL_DEADLINE_IN_PAST", "판매 마감 시각은 지금 이후여야 합니다.", HttpStatus.BAD_REQUEST),
    CART_INDIVIDUAL_DEADLINE_TOO_LATE("CART_INDIVIDUAL_DEADLINE_TOO_LATE", "판매 마감 시각은 지금으로부터 48시간 이내여야 합니다.", HttpStatus.BAD_REQUEST),
    CART_INDIVIDUAL_ALREADY_CLOSED("CART_INDIVIDUAL_ALREADY_CLOSED", "이미 마감된 공동구매입니다.", HttpStatus.CONFLICT),
    CART_INDIVIDUAL_SALE_CLOSED("CART_INDIVIDUAL_SALE_CLOSED", "판매가 마감된 공동구매입니다.", HttpStatus.CONFLICT);

    private final String code;
    private final String msg;
    private final HttpStatus status;
}
