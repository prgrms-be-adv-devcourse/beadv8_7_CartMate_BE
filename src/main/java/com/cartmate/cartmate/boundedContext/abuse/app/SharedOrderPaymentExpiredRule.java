package com.cartmate.cartmate.boundedContext.abuse.app;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRule;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRuleThreshold;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
class SharedOrderPaymentExpiredRule implements AbuseRule {

    static final AbuseRuleThreshold THRESHOLD = new AbuseRuleThreshold(Duration.ofDays(30), 1, 2, 3, 40);

    @Override
    public String code() {
        return "SHARED_ORDER_PAYMENT_EXPIRED";
    }

    @Override
    public AbuseSignalType signalType() {
        return AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED;
    }

    @Override
    public AbuseRuleThreshold threshold() {
        return THRESHOLD;
    }
}
