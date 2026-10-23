package com.cartmate.cartmate.global.code;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum SalesPostErrorCode implements ErrorCode {
    POST_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "POST_UPDATE_FAILED", "게시글 수정에 실패했습니다."),
    POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "POST_ACCESS_DENIED", "게시글 수정 권한이 없습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "게시글을 찾을 수 없습니다."),
    POST_INVALID_INPUT(HttpStatus.BAD_REQUEST, "POST_INVALID_INPUT", "유효성 검증 실패"),
    POST_PENDING_STATUS(HttpStatus.NOT_ACCEPTABLE, "POST_PENDING_STATUS", "유효성 검증 실패"),
    POST_RECOVERING_STATUS(HttpStatus.NOT_ACCEPTABLE, "POST_RECOVERING_STATUS", "유효성 검증 실패"),
    POST_SALE_PENDING_STATUS(HttpStatus.NOT_ACCEPTABLE, "POST_SALE_PENDING_STATUS", "등록 또는 삭제가 진행 중인 게시글입니다."),
    POST_NOT_RECOVERING_STATUS(HttpStatus.BAD_REQUEST, "POST_NOT_RECOVERING_STATUS", "복구중인 상태가 아닙니다.");

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
