package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRule;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRuleWeight;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
class SharedPurchaseConcurrentJoinRule implements AbuseRule {

    static final AbuseRuleWeight WEIGHT = new AbuseRuleWeight(Duration.ofDays(30), 10);

    @Override
    public String code() {
        return "SHARED_PURCHASE_CONCURRENT_JOIN";
    }

    @Override
    public AbuseSignalType signalType() {
        return AbuseSignalType.SHARED_PURCHASE_CONCURRENT_JOIN;
    }

    @Override
    public AbuseRuleWeight weight() {
        return WEIGHT;
    }
}
