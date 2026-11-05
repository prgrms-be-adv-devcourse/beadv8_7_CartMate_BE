package com.cartmate.cartmate.boundedContext.orderService.cart.out;

import com.cartmate.cartmate.boundedContext.etcService.shared.abuse.dto.AbuseRestrictionStatus;

public interface AbuseRestrictionClient {

    AbuseRestrictionStatus getRestriction(Long memberId);
}
