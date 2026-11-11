package com.cartmate.cartmate.boundedContext.memberService.member.out;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 인증 코드 상태를 Redis 해시 하나에 둔다 (키: verify:{용도}:{대상}). 가입 이메일 확인과 다음 단계 로그인 2차 인증이 같이 쓴다.
 * 코드 원문은 저장하지 않고 HMAC 해시만 둔다.
 *
 * <p>발송과 확인은 각각 Lua 스크립트 하나로 처리한다. Redis 왕복이 한 번이고, 동시에 들어온 요청이 같은 값을 읽고
 * 함께 통과하지 못한다 (예: 틀린 코드를 한꺼번에 많이 보내 시도 횟수 제한을 넘기는 것).
 *
 * <p>필드: hash(지금 유효한 코드), expiresAt · sentAt · verifiedUntil(epoch ms), sends(보낸 횟수), attempts(틀린 횟수).
 * 키 전체는 첫 발송부터 sendWindow 뒤 사라지므로, 보낸·틀린 횟수는 재발송해도 초기화되지 않는다.
 */
@Component
public class VerificationCodeStore {

    private static final String PREFIX = "verify:";

    // ARGV: hash, now, expiresAt, cooldownMs, maxSends, maxAttempts, windowMs
    // 반환: 0 발급, -1 발송 횟수 초과, -2 시도 횟수 초과, 양수 = 재발송까지 남은 ms
    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[2])
            local f = redis.call('HMGET', KEYS[1], 'sentAt', 'sends', 'attempts')
            local sentAt = tonumber(f[1])
            if sentAt and sentAt + tonumber(ARGV[4]) > now then
                return sentAt + tonumber(ARGV[4]) - now
            end
            local sends = tonumber(f[2] or '0')
            if sends >= tonumber(ARGV[5]) then return -1 end
            if tonumber(f[3] or '0') >= tonumber(ARGV[6]) then return -2 end
            redis.call('HSET', KEYS[1], 'hash', ARGV[1], 'sentAt', now, 'expiresAt', ARGV[3], 'sends', sends + 1)
            redis.call('HDEL', KEYS[1], 'verifiedUntil')
            if sends == 0 then redis.call('PEXPIRE', KEYS[1], ARGV[7]) end
            return 0
            """, Long.class);

    // ARGV: hash, now, maxAttempts, verifiedUntil, verifiedTtlMs
    // 반환: 0 확인됨(이미 확인됨 포함), -1 보낸 적 없음, -2 시도 횟수 초과, -3 만료, 양수 = 틀림(올린 뒤 틀린 횟수)
    // 해시 비교가 Lua 문자열 비교라도 괜찮다: 비교하는 값이 서버 비밀키 HMAC이라 요청하는 쪽이 고를 수 없다
    private static final DefaultRedisScript<Long> VERIFY = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
            local now = tonumber(ARGV[2])
            local f = redis.call('HMGET', KEYS[1], 'hash', 'expiresAt', 'attempts', 'verifiedUntil')
            local verifiedUntil = tonumber(f[4])
            if verifiedUntil and verifiedUntil > now then return 0 end
            if tonumber(f[3] or '0') >= tonumber(ARGV[3]) then return -2 end
            local expiresAt = tonumber(f[2])
            if not f[1] or not expiresAt or expiresAt <= now then return -3 end
            if f[1] ~= ARGV[1] then return redis.call('HINCRBY', KEYS[1], 'attempts', 1) end
            redis.call('HDEL', KEYS[1], 'hash')
            redis.call('HSET', KEYS[1], 'verifiedUntil', ARGV[4])
            redis.call('PEXPIRE', KEYS[1], ARGV[5])
            return 0
            """, Long.class);

    public enum IssueStatus { ISSUED, TOO_SOON, TOO_MANY_SENDS, TOO_MANY_ATTEMPTS }

    public record IssueResult(IssueStatus status, Duration retryAfter) {
    }

    public enum VerifyStatus { VERIFIED, NOT_SENT, TOO_MANY_ATTEMPTS, EXPIRED, MISMATCH }

    public record VerifyResult(VerifyStatus status, int attempts) {
    }

    private final StringRedisTemplate redis;

    @Autowired
    public VerificationCodeStore(@Qualifier("redisCoreConnectionFactory") RedisConnectionFactory connectionFactory) {
        this(new StringRedisTemplate(connectionFactory));
    }

    // 테스트에서 템플릿을 바꿔 끼우기 위한 생성자
    VerificationCodeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // 새 코드로 바꾼다: 이전 코드는 바로 못 쓰고, 틀린 횟수는 그대로, 보낸 횟수 +1
    public IssueResult issue(String key, String codeHash, Instant now, Duration codeTtl, Duration resendCooldown,
                             int maxSends, int maxAttempts, Duration sendWindow) {
        long result = run(ISSUE, key, codeHash, now.toEpochMilli(), now.plus(codeTtl).toEpochMilli(),
                resendCooldown.toMillis(), maxSends, maxAttempts, sendWindow.toMillis());
        if (result > 0) {
            return new IssueResult(IssueStatus.TOO_SOON, Duration.ofMillis(result));
        }
        IssueStatus status = result == 0 ? IssueStatus.ISSUED
                : result == -1 ? IssueStatus.TOO_MANY_SENDS : IssueStatus.TOO_MANY_ATTEMPTS;
        return new IssueResult(status, Duration.ZERO);
    }

    // 맞으면 코드를 지우고(다시 못 씀) verifiedTtl 동안 "확인됨", 틀리면 틀린 횟수 +1
    public VerifyResult verify(String key, String codeHash, Instant now, int maxAttempts, Duration verifiedTtl) {
        long result = run(VERIFY, key, codeHash, now.toEpochMilli(), maxAttempts,
                now.plus(verifiedTtl).toEpochMilli(), verifiedTtl.toMillis());
        if (result > 0) {
            return new VerifyResult(VerifyStatus.MISMATCH, (int) result);
        }
        VerifyStatus status = switch ((int) result) {
            case 0 -> VerifyStatus.VERIFIED;
            case -1 -> VerifyStatus.NOT_SENT;
            case -2 -> VerifyStatus.TOO_MANY_ATTEMPTS;
            default -> VerifyStatus.EXPIRED;
        };
        return new VerifyResult(status, 0);
    }

    public boolean isVerified(String key, Instant now) {
        Object until = redis.opsForHash().get(PREFIX + key, "verifiedUntil");
        return until != null && Long.parseLong(until.toString()) > now.toEpochMilli();
    }

    // 메일을 못 보냈을 때: 재발송 대기만 풀어 바로 다시 요청할 수 있게 한다 (보낸 횟수는 그대로 센다)
    public void releaseCooldown(String key) {
        redis.opsForHash().delete(PREFIX + key, "sentAt");
    }

    public void remove(String key) {
        redis.delete(PREFIX + key);
    }

    private long run(DefaultRedisScript<Long> script, String key, Object... args) {
        Long result = redis.execute(script, List.of(PREFIX + key),
                Arrays.stream(args).map(String::valueOf).toArray());
        if (result == null) {
            throw new IllegalStateException("인증 코드 저장소 응답이 없습니다.");
        }
        return result;
    }
}
