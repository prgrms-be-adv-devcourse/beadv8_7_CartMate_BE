package com.cartmate.cartmate.global.code;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum SalesPostErrorCode implements ErrorCode {
    POST_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "POST_UPDATE_FAILED", "게시글 수정에 실패했습니다."),
    POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "POST_ACCESS_DENIED", "게시글 수정 권한이 없습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    SalesPostErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public String getMsg() {
        return this.message;
    }
}
