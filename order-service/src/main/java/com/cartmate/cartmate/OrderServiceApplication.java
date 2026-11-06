package com.cartmate.cartmate;

import com.cartmate.cartmate.global.global.RedisConfig;
import com.cartmate.cartmate.global.global.RedissonConfig;
import com.cartmate.cartmate.global.global.S3Config;
import com.cartmate.cartmate.global.global.WebConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 주문 서버(장바구니 · 공동구매 · 주문 · 어뷰징 · 수요 매칭).
 *
 * <p>common의 global 설정을 그대로 쓰되, 주문 서버가 쓰지 않는 인프라 설정은 스캔에서 뺀다.
 * 빼지 않으면 Redis · S3 접속 정보가 없다는 이유로 서버가 뜨지 않는다.
 * <ul>
 *   <li>Redis · Redisson · 분산 락(global.lock): 지갑 · 회원 인증 저장소용. 주문 쪽은 DB 행 락 + ShedLock을 쓴다</li>
 *   <li>S3 · 이미지 정적 경로(WebConfig): 상품 이미지용</li>
 * </ul>
 * 위 두 필터는 {@code @SpringBootApplication}의 기본 스캔 필터다. 스캔을 직접 선언하면 기본 필터가 빠지므로 다시 넣는다.
 */
@SpringBootApplication
@ComponentScan(excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {RedisConfig.class, RedissonConfig.class, S3Config.class, WebConfig.class}),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.cartmate\\.cartmate\\.global\\.lock\\..*")
})
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
