package com.cartmate.cartmate.boundedContext.member.out;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// 휴대폰 인증 상태 임시 보관소 (서버 메모리). 서버가 여러 대가 되면 Redis로 교체한다.
@Component
public class PhoneVerificationStore {

    public static class Entry {
        private final String code;
        private final LocalDateTime codeExpiresAt;
        private int attempts;
        private LocalDateTime verifiedUntil;

        Entry(String code, LocalDateTime codeExpiresAt) {
            this.code = code;
            this.codeExpiresAt = codeExpiresAt;
        }

        public String getCode() { return code; }
        public LocalDateTime getCodeExpiresAt() { return codeExpiresAt; }
        public int getAttempts() { return attempts; }
        public void increaseAttempts() { attempts++; }
        public void markVerified(LocalDateTime until) { this.verifiedUntil = until; }
        public boolean isVerified(LocalDateTime now) { return verifiedUntil != null && verifiedUntil.isAfter(now); }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public void saveCode(String phone, String code, LocalDateTime expiresAt) {
        store.put(phone, new Entry(code, expiresAt));
    }

    public Optional<Entry> find(String phone) {
        return Optional.ofNullable(store.get(phone));
    }

    public void remove(String phone) {
        store.remove(phone);
    }
}