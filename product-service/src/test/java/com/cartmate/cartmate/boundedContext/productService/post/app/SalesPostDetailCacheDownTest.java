package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.post.dto.SalesPostDetailInventories;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 캐시 Redis에 연결할 수 없어도 상세 재고 조회는 실패하지 않고 캐시 없이 상품 서버(DB)로 간다.
 * 아무도 듣지 않는 포트를 캐시 Redis 주소로 줘서 장애를 흉내 낸다.
 */
@SpringBootTest(properties = {
        "cartmate.scheduling.enabled=false",
        "custom.redis.cache.host=localhost"
})
@ActiveProfiles("test")
class SalesPostDetailCacheDownTest {

    private static final int DEAD_PORT = unusedPort();

    @DynamicPropertySource
    static void deadRedis(DynamicPropertyRegistry registry) {
        registry.add("custom.redis.cache.port", () -> DEAD_PORT);
    }

    @Autowired
    SalesPostDetailInventoryLoader loader;
    @MockitoSpyBean
    SalesPostApiClient salesPostApiClient;

    @Test
    @DisplayName("Redis 장애: 오류 없이 매번 상품 서버에서 가져오고, 0.5초 제한으로 오래 붙잡히지 않는다")
    void loadsWithoutCacheWhenRedisIsDown() {
        doReturn(List.of()).when(salesPostApiClient).getProduct(1L);

        long started = System.nanoTime();
        SalesPostDetailInventories first = loader.load(1L);
        SalesPostDetailInventories second = loader.load(1L);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(first.products()).isEmpty();
        assertThat(second.products()).isEmpty();
        verify(salesPostApiClient, times(2)).getProduct(1L);   // 캐시를 못 쓰니 매번 간다
        assertThat(elapsedMillis).isLessThan(5_000);           // 기본 60초 대기가 아니다
    }

    private static int unusedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
