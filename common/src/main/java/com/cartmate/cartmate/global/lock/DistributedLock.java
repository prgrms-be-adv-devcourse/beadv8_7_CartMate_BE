package com.cartmate.cartmate.global.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 메서드 실행 동안 Redis 분산 락을 잡는다. 같은 클래스 안에서 this로 호출하면 프록시를 거치지 않아 락이 걸리지 않는다.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    // SpEL로 계산한 락 키. 메서드 파라미터 이름을 변수로 쓴다. 예: "'wallet-charge:' + #orderNumber"
    String key();

    // 락을 얻기 위해 기다리는 시간(초). 넘기면 LOCK_ACQUISITION_FAILED를 던진다.
    long waitSeconds() default 3;
}
