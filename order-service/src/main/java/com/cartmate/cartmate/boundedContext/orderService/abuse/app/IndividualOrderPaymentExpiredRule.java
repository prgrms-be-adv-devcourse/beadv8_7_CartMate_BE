package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRule;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRuleWeight;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
class IndividualOrderPaymentExpiredRule implements AbuseRule {

    static final AbuseRuleWeight WEIGHT = new AbuseRuleWeight(Duration.ofDays(30), 5);

    @Override
    public String code() {
        return "INDIVIDUAL_ORDER_PAYMENT_EXPIRED";
    }

    @Override
    public AbuseSignalType signalType() {
        return AbuseSignalType.INDIVIDUAL_ORDER_PAYMENT_EXPIRED;
    }

    @Override
    public AbuseRuleWeight weight() {
        return WEIGHT;
    }
}
