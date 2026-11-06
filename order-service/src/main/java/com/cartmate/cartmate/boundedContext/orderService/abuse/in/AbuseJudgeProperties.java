package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "cartmate.abuse.judge")
record AbuseJudgeProperties(
        @DefaultValue("100") @Positive int batchSize,
        @DefaultValue("PT10S") @NotNull Duration timeBudget
) {
}
