package com.cartmate.cartmate.boundedContext.productService.code;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum PaybackErrorCode implements ErrorCode {
    PAYBACK_INVALID_CONDITION(HttpStatus.BAD_REQUEST, "PAYBACK_INVALID_CONDITION",
            "페이백 기준 수량은 2개 이상 100,000개 이하, 개당 금액은 1원 이상 100,000원 이하여야 합니다."),
    PAYBACK_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYBACK_PRODUCT_NOT_FOUND", "페이백 조건을 걸 상품이 없습니다."),
    PAYBACK_SELLER_ONLY(HttpStatus.FORBIDDEN, "PAYBACK_SELLER_ONLY", "판매자만 페이백 조건을 걸 수 있습니다."),
    PAYBACK_NOT_PRODUCT_OWNER(HttpStatus.FORBIDDEN, "PAYBACK_NOT_PRODUCT_OWNER", "자기 상품에만 페이백 조건을 걸 수 있습니다."),
    PAYBACK_CONDITION_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYBACK_CONDITION_NOT_FOUND", "해제할 페이백 조건이 없습니다."),
    PAYBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYBACK_NOT_FOUND", "존재하지 않는 페이백 지급입니다."),
    PAYBACK_NOT_FAILED(HttpStatus.CONFLICT, "PAYBACK_NOT_FAILED", "실패로 확정된 페이백만 다시 요청할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    PaybackErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public String getMsg() {
        return this.message;
    }
}
