package com.cartmate.cartmate.global.outbox;

/**
 * 싱크가 던지는 "다시 시도해도 똑같이 실패하는" 예외. 릴레이는 이 예외를 받으면 백오프 없이 바로 FAILED로 바꾼다.
 * 예: 봉투의 event_type을 아는 클래스로 바꿀 수 없음, payload가 이벤트 형태와 맞지 않음.
 */
public class NonRetryableRelayException extends RuntimeException {

    public NonRetryableRelayException(String message, Throwable cause) {
        super(message, cause);
    }
}
