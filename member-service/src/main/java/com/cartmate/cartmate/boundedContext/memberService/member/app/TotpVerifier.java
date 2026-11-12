package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * TOTP (RFC 6238, 30초·6자리·HMAC-SHA1 — Google·Microsoft Authenticator 기본값).
 * 코드 계산은 java-otp 라이브러리에 맡기고, 몇 구간까지 허용할지·재사용 방지는 여기와 AdminOtpUseCase가 정한다.
 */
@Component
class TotpVerifier {

    static final Duration STEP = Duration.ofSeconds(30);
    private static final int DIGITS = 6;
    private static final int SECRET_BYTES = 20; // 160비트 (HMAC-SHA1 출력 길이)
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TimeBasedOneTimePasswordGenerator generator =
            new TimeBasedOneTimePasswordGenerator(STEP, DIGITS, TimeBasedOneTimePasswordGenerator.TOTP_ALGORITHM_HMAC_SHA1);

    byte[] newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return secret;
    }

    // OTP 앱에 넣을 주소 (프론트가 QR로 그린다). 앱에 직접 입력할 수 있게 Base32 비밀키도 따로 준다
    String otpauthUri(String accountName, byte[] secret) {
        String label = URLEncoder.encode("CartMate:" + accountName, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + base32(secret) + "&issuer=CartMate&algorithm=SHA1&digits=6&period=30";
    }

    String base32(byte[] secret) {
        return new Base32().encodeToString(secret).replace("=", "");
    }

    /**
     * 맞으면 그 코드가 속한 30초 구간 번호. 휴대폰 시계가 조금 어긋나도 되게 앞뒤 한 구간(±30초)까지 본다.
     * 재사용 방지(이미 쓴 구간인지)는 호출하는 쪽이 이 번호로 확인한다.
     */
    OptionalLong verify(byte[] secret, String code, Instant now) {
        if (code == null || !code.matches("\\d{6}")) {
            return OptionalLong.empty();
        }
        SecretKeySpec key = new SecretKeySpec(secret, generator.getAlgorithm());
        long current = stepOf(now);
        for (long step = current - 1; step <= current + 1; step++) {
            String expected = String.format("%06d", generate(key, Instant.ofEpochSecond(step * STEP.toSeconds())));
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    static long stepOf(Instant time) {
        return time.getEpochSecond() / STEP.toSeconds();
    }

    private int generate(SecretKeySpec key, Instant at) {
        try {
            return generator.generateOneTimePassword(key, at);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
