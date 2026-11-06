package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRule;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRuleWeight;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
class SharedOrderPaymentExpiredRule implements AbuseRule {

    static final AbuseRuleWeight WEIGHT = new AbuseRuleWeight(Duration.ofDays(30), 3);

    @Override
    public String code() {
        return "SHARED_ORDER_PAYMENT_EXPIRED";
    }

    @Override
    public AbuseSignalType signalType() {
        return AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED;
    }

    @Override
    public AbuseRuleWeight weight() {
        return WEIGHT;
    }
}
