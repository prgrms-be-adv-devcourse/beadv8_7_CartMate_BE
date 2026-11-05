package com.cartmate.cartmate.boundedContext.orderService.coupon.domain;

import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {

    private static Coupon fixed(long value) {
        return Coupon.create("정액", CouponDiscountType.FIXED, value, null, 0, CouponScope.ALL, null,
                CouponIssueType.DOWNLOAD, null, null, null, null, 30, null, 1L);
    }

    private static Coupon rate(long percent, Long max) {
        return Coupon.create("정률", CouponDiscountType.RATE, percent, max, 0, CouponScope.ALL, null,
                CouponIssueType.DOWNLOAD, null, null, null, null, 30, null, 1L);
    }

    @Test
    void 정액은_할인_값만큼_대상_금액을_넘지_않게() {
        assertThat(fixed(2_000).discountFor(14_000)).isEqualTo(2_000);
        assertThat(fixed(5_000).discountFor(4_000)).isEqualTo(4_000); // 대상 금액까지만
        assertThat(fixed(2_000).discountFor(0)).isZero();
    }

    @Test
    void 정률은_원_미만_버림_최대_할인까지() {
        assertThat(rate(10, null).discountFor(14_000)).isEqualTo(1_400);
        assertThat(rate(10, null).discountFor(14_999)).isEqualTo(1_499); // 1,499.9 → 버림
        assertThat(rate(10, 1_000L).discountFor(14_000)).isEqualTo(1_000); // 최대 할인
    }

    @Test
    void 최소_주문_금액() {
        Coupon coupon = Coupon.create("최소", CouponDiscountType.FIXED, 1_000, null, 30_000, CouponScope.ALL, null,
                CouponIssueType.DOWNLOAD, null, null, null, null, 30, null, 1L);
        assertThat(coupon.meetsMinimum(29_999)).isFalse();
        assertThat(coupon.meetsMinimum(30_000)).isTrue();
    }

    @Test
    void 잘못된_설정은_만들_수_없다() {
        assertThatThrownBy(() -> rate(101, null)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> Coupon.create("판매자", CouponDiscountType.FIXED, 1_000, null, 0, CouponScope.SELLER, null,
                CouponIssueType.DOWNLOAD, null, null, null, null, 30, null, 1L)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> Coupon.create("기간 둘 다", CouponDiscountType.FIXED, 1_000, null, 0, CouponScope.ALL, null,
                CouponIssueType.DOWNLOAD, null, null, null, LocalDateTime.now(), 30, null, 1L)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> Coupon.create("자동 조건 없음", CouponDiscountType.FIXED, 1_000, null, 0, CouponScope.ALL, null,
                CouponIssueType.AUTO, null, null, null, null, 30, null, 1L)).isInstanceOf(ServiceException.class);
    }

    @Test
    void 발급_후_N일이면_N일째_날_다음날_0시_전까지() {
        LocalDateTime issuedAt = LocalDateTime.of(2026, 11, 5, 15, 0);
        // 30일: 11/5 ~ 12/4 → 12/5 0시부터 만료
        assertThat(fixed(1_000).expiresAtFor(issuedAt)).isEqualTo(LocalDateTime.of(2026, 12, 5, 0, 0));
    }

    @Test
    void 중지하거나_기간_밖이면_발급_불가() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 5, 12, 0);
        Coupon period = Coupon.create("기간", CouponDiscountType.FIXED, 1_000, null, 0, CouponScope.ALL, null,
                CouponIssueType.DOWNLOAD, null, now.plusDays(1), now.plusDays(2), null, 30, null, 1L);
        assertThat(period.isIssuable(now)).isFalse();
        assertThat(period.isIssuable(now.plusDays(1))).isTrue();

        Coupon stopped = fixed(1_000);
        stopped.stop();
        assertThat(stopped.isIssuable(now)).isFalse();
    }
}