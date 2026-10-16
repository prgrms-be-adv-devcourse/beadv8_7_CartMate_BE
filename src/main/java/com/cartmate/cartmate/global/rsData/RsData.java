package com.cartmate.cartmate.global.rsData;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RsData<T> {

    private final String resultCode;
    private final String msg;
    private final T data;

    public RsData(String resultCode, String msg) {
        this(resultCode, msg, null);
    }

    public int getStatusCode() {
        return Integer.parseInt(resultCode.split("-", 2)[0]);
    }
}
