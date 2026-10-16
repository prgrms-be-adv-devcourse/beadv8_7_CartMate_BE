package com.cartmate.cartmate.global.code;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum SuccessCode {
    POST_CREATED(HttpStatus.OK, "POST_PUBLISHED", "게시글이 등록되었습니다."),
    PRODUCT_CREATED(HttpStatus.CREATED, "PRODUCT_CREATED", "상품 정보가 등록되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    SuccessCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
