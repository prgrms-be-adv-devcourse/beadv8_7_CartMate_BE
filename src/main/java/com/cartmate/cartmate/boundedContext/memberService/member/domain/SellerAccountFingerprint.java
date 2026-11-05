package com.cartmate.cartmate.boundedContext.memberService.member.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// 계좌 확인에 쓴 입력값을 하나의 해시로 묶는다. 입력값이 하나라도 달라지면 해시가 달라진다.
public final class SellerAccountFingerprint {

    private SellerAccountFingerprint() {
    }

    public static String of(SellerType sellerType, String bankName, String accountNumber, String accountHolder,
                            String businessName, String representativeName, String businessNumber) {
        boolean business = sellerType == SellerType.BUSINESS;
        StringBuilder raw = new StringBuilder();
        // 길이를 앞에 붙여 필드 경계가 섞여도 같은 문자열이 되지 않게 한다. 개인은 사업자 정보를 쓰지 않는다.
        for (String field : new String[]{
                sellerType.name(), bankName, accountNumber, accountHolder,
                business ? nullToEmpty(businessName) : "",
                business ? nullToEmpty(representativeName) : "",
                business ? nullToEmpty(businessNumber) : ""}) {
            raw.append(field.length()).append(':').append(field);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
