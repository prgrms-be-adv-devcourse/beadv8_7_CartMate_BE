package com.cartmate.cartmate.global.outbox;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 아웃박스 설정값과 ShedLock. 락 제공자는 같은 DB의 {@code shedlock} 테이블(JDBC)이다.
 * Redis 제공자를 쓰지 않는 이유: 테스트가 Testcontainers MySQL뿐이고 Redis 인프라는 아직 세팅 중.
 * 지금 {@code @SchedulerLock}이 붙은 메서드는 {@link OutboxRelay#relay()} 하나다. cart · order 스케줄러는 ARCH-10.
 */
@Configuration
@EnableConfigurationProperties(OutboxRelayProperties.class)
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class OutboxConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
