package com.cartmate.cartmate.boundedContext.cart.out;

import com.cartmate.cartmate.boundedContext.abuse.app.AbuseFacade;
import com.cartmate.cartmate.shared.abuse.dto.AbuseRestrictionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AbuseFacadeAbuseRestrictionClient implements AbuseRestrictionClient {

    private final AbuseFacade abuseFacade;

    @Override
    public AbuseRestrictionStatus getRestriction(Long memberId) {
        return abuseFacade.getRestriction(memberId);
    }
}
