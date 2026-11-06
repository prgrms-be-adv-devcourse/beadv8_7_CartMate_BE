package com.cartmate.cartmate.boundedContext.orderService.cart.out;

import com.cartmate.cartmate.shared.abuse.dto.AbuseRestrictionStatus;

public interface AbuseRestrictionClient {

    AbuseRestrictionStatus getRestriction(Long memberId);
}
