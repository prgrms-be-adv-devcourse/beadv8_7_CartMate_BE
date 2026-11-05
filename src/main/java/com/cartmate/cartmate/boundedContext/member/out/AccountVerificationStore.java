package com.cartmate.cartmate.boundedContext.member.out;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

// 계좌 확인 통과 표시와 호출 횟수를 Redis에 잠깐 보관한다. 값이 사라지면 판매자가 다시 확인하면 된다.
@Component
public class AccountVerificationStore {

    private static final String VERIFIED_KEY = "seller-account:verified:";
    private static final String ATTEMPTS_KEY = "seller-account:attempts:";

    private static final DefaultRedisScript<Long> ATTEMPT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;

    @Autowired
    public AccountVerificationStore(@Qualifier("redisCoreConnectionFactory") RedisConnectionFactory connectionFactory) {
        this(new StringRedisTemplate(connectionFactory));
    }

    // 테스트에서 템플릿을 바꿔 끼우기 위한 생성자
    AccountVerificationStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void saveVerified(Long memberId, String fingerprint, Duration ttl) {
        redis.opsForValue().set(VERIFIED_KEY + memberId, fingerprint, ttl);
    }

    public boolean isVerified(Long memberId, String fingerprint) {
        return fingerprint.equals(redis.opsForValue().get(VERIFIED_KEY + memberId));
    }

    public void remove(Long memberId) {
        redis.delete(VERIFIED_KEY + memberId);
    }

    // 성공한 호출도 센다. 첫 호출에서 윈도 만료 시간을 건다.
    // 증가와 만료 설정을 한 스크립트로 처리한다. 둘을 따로 보내면 그 사이에 끊겼을 때 만료 없는 카운터가 남아 계속 차단된다.
    public boolean tryAcquireAttempt(Long memberId, int maxAttempts, Duration window) {
        long windowSeconds = Math.max(1, window.toSeconds());
        Long count = redis.execute(ATTEMPT_SCRIPT, List.of(ATTEMPTS_KEY + memberId), String.valueOf(windowSeconds));
        return count != null && count <= maxAttempts;
    }
}
