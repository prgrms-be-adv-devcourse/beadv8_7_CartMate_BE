package com.cartmate.cartmate.boundedContext.orderService.coupon.domain;

import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 보유 → 사용 예정 → 사용 완료, 결제 전 취소·전체 취소면 보유로
class MemberCouponTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);

    private static MemberCoupon issued() {
        Coupon coupon = Coupon.create("쿠폰", CouponDiscountType.FIXED, 1_000, null, 0, CouponScope.ALL, null,
                CouponIssueType.DOWNLOAD, null, null, null, null, 7, null, 1L);
        ReflectionTestUtils.setField(coupon, "id", 10L);
        return MemberCoupon.issue(coupon, 1L, NOW);
    }

    @Test
    void 주문에_고르고_결제하면_사용_완료() {
        MemberCoupon coupon = issued();
        coupon.reserve("ORDER", 5L, NOW);
        assertThat(coupon.getStatus()).isEqualTo(MemberCouponStatus.RESERVED);
        coupon.use("ORDER", 5L, NOW);
        assertThat(coupon.getStatus()).isEqualTo(MemberCouponStatus.USED);
        coupon.use("ORDER", 5L, NOW); // 같은 주문으로 다시 불러도 그대로
        assertThat(coupon.getStatus()).isEqualTo(MemberCouponStatus.USED);
    }

    @Test
    void 다른_주문에_쓰는_중이면_고를_수_없다() {
        MemberCoupon coupon = issued();
        coupon.reserve("ORDER", 5L, NOW);
        assertThatThrownBy(() -> coupon.reserve("ORDER", 6L, NOW)).isInstanceOf(ServiceException.class);
    }

    @Test
    void 취소하면_그_주문에_쓴_쿠폰만_돌아온다() {
        MemberCoupon coupon = issued();
        coupon.reserve("ORDER", 5L, NOW);
        coupon.use("ORDER", 5L, NOW);

        coupon.restore("ORDER", 6L); // 다른 주문 취소 → 그대로
        assertThat(coupon.getStatus()).isEqualTo(MemberCouponStatus.USED);

        coupon.restore("ORDER", 5L);
        assertThat(coupon.getStatus()).isEqualTo(MemberCouponStatus.AVAILABLE);
        assertThat(coupon.getUsedOrderId()).isNull();
    }

    @Test
    void 기간이_지나면_만료로_보이고_고를_수_없다() {
        MemberCoupon coupon = issued();
        LocalDateTime later = NOW.plusDays(8);
        assertThat(coupon.displayStatus(later)).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> coupon.reserve("ORDER", 5L, later)).isInstanceOf(ServiceException.class);
    }
}
