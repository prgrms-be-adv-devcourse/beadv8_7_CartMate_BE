package com.cartmate.cartmate.boundedContext.productService.coupon.in;

import com.cartmate.cartmate.boundedContext.productService.coupon.app.CouponFacade;
import com.cartmate.cartmate.shared.member.event.MemberSignedUpEvent;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 가입 쿠폰 자동 발급. 가입 이벤트를 Outbox 릴레이로 받는다 (같은 트랜잭션 리스너가 아님 → 쿠폰 발급이 실패해도 가입은 그대로).
 * 같은 이벤트가 두 번 와도 회원당 한 장이라 한 번만 발급된다. Kafka로 바꾸면 토픽 cartmate.member.member-signed-up으로 받는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponMemberEventConsumer {

    private final CouponFacade couponFacade;

    @TransactionalEventListener(fallbackExecution = true)
    public void handle(OutboxMessageRelayed message) {
        if (message.event() instanceof MemberSignedUpEvent event) {
            int issued = couponFacade.issueSignupCoupons(event.member().id());
            if (issued > 0) {
                log.info("가입 쿠폰 {}장 발급: 회원 #{}", issued, event.member().id());
            }
        }
    }
}
