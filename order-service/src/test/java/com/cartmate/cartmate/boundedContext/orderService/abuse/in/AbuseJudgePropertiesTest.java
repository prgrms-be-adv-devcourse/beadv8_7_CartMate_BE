package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseJudgePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    @DisplayName("설정이 없으면 배치 100명 · 시간 예산 10초를 쓴다")
    void usesDefaults() {
        runner.run(context -> {
            AbuseJudgeProperties properties = context.getBean(AbuseJudgeProperties.class);
            assertThat(properties.batchSize()).isEqualTo(100);
            assertThat(properties.timeBudget()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    @DisplayName("배치 크기와 시간 예산을 설정값(환경 변수 CARTMATE_ABUSE_JUDGE_BATCHSIZE 등)으로 바꿀 수 있다")
    void bindsOverrides() {
        runner.withPropertyValues("cartmate.abuse.judge.batch-size=30", "cartmate.abuse.judge.time-budget=PT3S")
                .run(context -> {
                    AbuseJudgeProperties properties = context.getBean(AbuseJudgeProperties.class);
                    assertThat(properties.batchSize()).isEqualTo(30);
                    assertThat(properties.timeBudget()).isEqualTo(Duration.ofSeconds(3));
                });
    }

    @Test
    @DisplayName("배치 크기가 0 이하면 기동에 실패한다")
    void rejectsNonPositiveBatchSize() {
        runner.withPropertyValues("cartmate.abuse.judge.batch-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AbuseJudgeProperties.class)
    static class PropertiesConfig {
    }
}
