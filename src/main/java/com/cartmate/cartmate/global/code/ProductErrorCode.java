package com.cartmate.cartmate.global.code;

import com.cartmate.cartmate.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ProductErrorCode implements ErrorCode {
    PRODUCT_COUNT_INVALID(HttpStatus.BAD_REQUEST, "PRODUCT_COUNT_INVALID", "등록이 가능한 상품 수량을 초과했습니다."),
    PRODUCT_CREATE_INVALID(HttpStatus.BAD_REQUEST, "PRODUCT_CREATE_INVALID", "잘못된 상품등록 요청입니다."),
    PRODUCT_CONNECT_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_CREATE_INVALID", "잘못된 상품등록 요청입니다."),
    PRODUCT_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PRODUCT_CREATE_FAILED", "상품정보 등록에 실패했습니다."),
    PRODUCT_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PRODUCT_UPDATE_FAILED", "상품정보 수정에 실패했습니다."),
    PRODUCT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PRODUCT_ACCESS_DENIED", "상품정보 수정 권한이 없습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품 정보가 없습니다."),
    PRODUCT_DELETE_INVALID(HttpStatus.BAD_REQUEST, "PRODUCT_DELETE_INVALID", "잘못된 상품삭제 요청입니다."),
    PRODUCT_DELETE_FAILED(HttpStatus.BAD_REQUEST, "PRODUCT_DELETE_FAILED", "상품정보 삭제에 실패했습니다."),

    PRODUCT_IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PRODUCT_IMAGE_UPLOAD_FAILED", "등록이 가능한 상품 수량을 초과했습니다."),
    PRODUCT_IMAGE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "PRODUCT_IMAGE_INVALID_TYPE", "잘못된 형식의 이미지입니다."),
    PRODUCT_IMAGE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "IMAGE_COUNT_EXCEEDED", "업로드 가능한 이미지 수량을 초과했습니다."),
    PRODUCT_IMAGE_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PRODUCT_IMAGE_UPDATE_FAILED", "이미지 정보 수정에 실패했습니다."),
    PRODUCT_IMAGE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PRODUCT_IMAGE_ACCESS_DENIED", "이미지 정보 수정 권한이 없습니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;

    ProductErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public String getMsg() {
        return this.message;
    }
}
