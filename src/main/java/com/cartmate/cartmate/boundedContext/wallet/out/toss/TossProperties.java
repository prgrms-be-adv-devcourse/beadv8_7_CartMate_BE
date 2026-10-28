package com.cartmate.cartmate.boundedContext.wallet.out.toss;

import org.springframework.boot.context.properties.ConfigurationProperties;

// custom.wallet.toss 설정. 시크릿 키는 환경 변수 TOSS_SECRET_KEY로 넣는다.
@ConfigurationProperties(prefix = "custom.wallet.toss")
public record TossProperties(String baseUrl, String secretKey) {
}
