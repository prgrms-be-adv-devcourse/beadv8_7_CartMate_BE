package com.cartmate.cartmate.boundedContext.member.out.account;

import org.springframework.boot.context.properties.ConfigurationProperties;

// custom.openbanking 설정. live일 때만 키를 쓰고, 값이 비어 있으면 기동에 실패한다.
// 키는 환경 변수 OPENBANKING_CLIENT_ID, OPENBANKING_CLIENT_SECRET, OPENBANKING_ORG_CODE로 넣는다.
@ConfigurationProperties(prefix = "custom.openbanking")
public record OpenBankingProperties(String mode, String clientId, String clientSecret, String orgCode) {

    public OpenBankingProperties {
        mode = (mode == null || mode.isBlank()) ? "fake" : mode;
        if ("live".equals(mode) && (isBlank(clientId) || isBlank(clientSecret) || isBlank(orgCode))) {
            throw new IllegalStateException(
                    "custom.openbanking.mode=live 에는 OPENBANKING_CLIENT_ID, OPENBANKING_CLIENT_SECRET, OPENBANKING_ORG_CODE가 필요합니다.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
