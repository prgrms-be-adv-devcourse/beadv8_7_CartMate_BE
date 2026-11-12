package com.cartmate.cartmate.boundedContext.memberService.member.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 인증 코드 만들기와 해시.
 *
 * <p>6자리 코드는 100만 개뿐이라 단순 SHA-256이면 저장소가 새어 나갔을 때 전부 대입해 풀린다.
 * 서버 비밀키로 HMAC-SHA-256을 쓰고, 같은 코드라도 대상마다 해시가 다르도록 키(용도:대상)를 함께 넣는다.
 */
@Component
public class VerificationCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec secretKey;

    public VerificationCodeHasher(@Value("${cartmate.mfa.code-secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("cartmate.mfa.code-secret은 32자 이상이어야 합니다.");
        }
        this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String newCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    // Mac은 스레드 안전하지 않아 호출마다 만든다 (키는 한 번만 만들어 둠)
    public String hash(String key, String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);
            return HexFormat.of().formatHex(mac.doFinal((key + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("인증 코드 해시를 만들지 못했습니다.", e);
        }
    }
}
