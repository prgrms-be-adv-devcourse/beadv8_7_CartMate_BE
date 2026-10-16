package com.cartmate.cartmate.global.response;

import com.cartmate.cartmate.global.code.ErrorCode;
import com.cartmate.cartmate.global.code.SuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ResponseData<T> {
    private final String code;
    private final String message;
    private final T data;

    public ResponseData(SuccessCode successCode, T data) {
        this.code = successCode.getCode();
        this.message = successCode.getMessage();
        this.data = data;
    }

    public ResponseData(SuccessCode successCode) {
        this.code = successCode.getCode();
        this.message = successCode.getMessage();
        this.data = null;
    }

    public ResponseData(ErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.data = null;
    }
}
