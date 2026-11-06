package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRule;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRuleWeight;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
class SharedPurchaseLeftRule implements AbuseRule {

    static final AbuseRuleWeight WEIGHT = new AbuseRuleWeight(Duration.ofDays(30), 8);

    @Override
    public String code() {
        return "SHARED_PURCHASE_LEFT";
    }

    @Override
    public AbuseSignalType signalType() {
        return AbuseSignalType.SHARED_PURCHASE_LEFT;
    }

    @Override
    public AbuseRuleWeight weight() {
        return WEIGHT;
    }
}
