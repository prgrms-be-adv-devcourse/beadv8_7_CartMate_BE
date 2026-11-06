package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

public final class AbuseIdentityHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] KEY = "cartmate-abuse-identity-v1:86a707f4d1a293ccd3a7c1f37b8e500706500fd3ffbb19b6a3eec4be5a4f282b".getBytes(StandardCharsets.UTF_8);
    private static final int MIN_PHONE_DIGITS = 9;

    private AbuseIdentityHasher() {
    }

    public static Optional<String> phone(String receiverPhone) {
        if (receiverPhone == null) {
            return Optional.empty();
        }
        String digits = receiverPhone.replaceAll("[^0-9]", "");
        return digits.length() < MIN_PHONE_DIGITS ? Optional.empty() : Optional.of(hmac(AbuseIdentityType.PHONE, digits));
    }

    public static Optional<String> address(String zipCode, String address, String addressDetail) {
        if (normalize(address).isEmpty() || normalize(addressDetail).isEmpty()) {
            return Optional.empty();
        }
        String normalized = normalize(zipCode) + "|" + normalize(address) + "|" + normalize(addressDetail);
        return Optional.of(hmac(AbuseIdentityType.ADDRESS, normalized));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }

    private static String hmac(AbuseIdentityType type, String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(KEY, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal((type.name() + ":" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("어뷰징 식별 해시를 만들 수 없음", e);
        }
    }
}
