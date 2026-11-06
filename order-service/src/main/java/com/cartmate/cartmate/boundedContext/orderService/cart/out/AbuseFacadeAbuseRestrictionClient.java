package com.cartmate.cartmate.boundedContext.orderService.cart.out;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.shared.abuse.dto.AbuseRestrictionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AbuseFacadeAbuseRestrictionClient implements AbuseRestrictionClient {

    private final AbuseFacade abuseFacade;

    @Override
    public AbuseRestrictionStatus getRestriction(Long memberId) {
        try {
            return abuseFacade.getRestriction(memberId);
        } catch (RuntimeException e) {
            log.warn("어뷰징 제한 조회 실패, 제한 없음으로 처리: memberId={}", memberId, e);
            return AbuseRestrictionStatus.none(memberId);
        }
    }
}
