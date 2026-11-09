package com.cartmate.cartmate.support;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.SpringProperties;
import org.springframework.test.context.cache.ContextCache;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@CartMateIntegrationTest
class TestConnectionBudgetTest {

    private static final int RESERVED_CONNECTIONS = 10;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("캐시된 컨텍스트가 모두 풀을 꽉 채워도 MySQL max_connections를 넘지 않는다")
    void cachedContextsCannotExhaustConnections() throws SQLException {
        String cacheMax = SpringProperties.getProperty(ContextCache.MAX_CONTEXT_CACHE_SIZE_PROPERTY_NAME);
        assertThat(cacheMax)
                .as("test 리소스 spring.properties에 " + ContextCache.MAX_CONTEXT_CACHE_SIZE_PROPERTY_NAME + "가 있어야 한다")
                .isNotNull();
        int poolSize = dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize();
        int maxConnections = maxConnections();

        assertThat(Integer.parseInt(cacheMax) * poolSize)
                .as("컨텍스트 캐시 상한 %s × Hikari 풀 %d ≤ max_connections %d - 여유 %d (PR #82 Too many connections)",
                        cacheMax, poolSize, maxConnections, RESERVED_CONNECTIONS)
                .isLessThanOrEqualTo(maxConnections - RESERVED_CONNECTIONS);
    }

    private int maxConnections() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT @@max_connections")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
