package com.cartmate.cartmate.global.outbox;

public class NonRetryableRelayException extends RuntimeException {

    public NonRetryableRelayException(String message, Throwable cause) {
        super(message, cause);
    }
}
