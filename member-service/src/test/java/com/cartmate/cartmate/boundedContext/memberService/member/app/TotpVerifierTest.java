package com.cartmate.cartmate.boundedContext.memberService.member.app;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// TOTP 계산이 표준(RFC 6238 부록 B, SHA-1)과 같은지, 허용 구간이 앞뒤 30초뿐인지
class TotpVerifierTest {

    private final TotpVerifier totp = new TotpVerifier();
    private final byte[] rfcSecret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void RFC_6238_테스트_값과_같고_그_구간_번호를_돌려준다() {
        // RFC 값은 8자리(94287082, 07081804)라 마지막 6자리가 6자리 OTP
        assertThat(totp.verify(rfcSecret, "287082", Instant.ofEpochSecond(59))).hasValue(1L);
        assertThat(totp.verify(rfcSecret, "081804", Instant.ofEpochSecond(1111111109))).hasValue(37037036L);
    }

    @Test
    void 앞뒤_30초까지만_허용하고_그_밖은_거절() {
        Instant codeTime = Instant.ofEpochSecond(1111111109);
        assertThat(totp.verify(rfcSecret, "081804", codeTime.plusSeconds(30))).isPresent();   // 휴대폰이 30초 느림
        assertThat(totp.verify(rfcSecret, "081804", codeTime.plusSeconds(90))).isEmpty();
        assertThat(totp.verify(rfcSecret, "000000", codeTime)).isEmpty();
        assertThat(totp.verify(rfcSecret, "12345", codeTime)).isEmpty();
    }

    @Test
    void 앱에_넣을_주소는_Base32_비밀키와_발급자를_담는다() {
        String uri = totp.otpauthUri("admin@cartmate.com", rfcSecret);
        assertThat(uri).startsWith("otpauth://totp/CartMate%3Aadmin%40cartmate.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
                .contains("issuer=CartMate", "digits=6", "period=30");
    }
}
