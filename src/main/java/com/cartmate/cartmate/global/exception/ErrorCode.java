package com.cartmate.cartmate.global.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    HttpStatus getStatus();
    String getCode();
    String getMsg();

    default String resultCode() {
        return getStatus().value() + "-" + getCode();
    }
}
