package com.cartmate.cartmate.boundedContext.orderService.coupon.app;

import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.Coupon;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.CouponDiscountType;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.CouponIssueType;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.CouponScope;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.MemberCoupon;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.MemberCouponStatus;
import com.cartmate.cartmate.boundedContext.orderService.coupon.exception.CouponErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.coupon.out.CouponRepository;
import com.cartmate.cartmate.boundedContext.orderService.coupon.out.MemberCouponRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.coupon.dto.CouponOption;
import com.cartmate.cartmate.shared.coupon.dto.CouponOrderTarget;
import com.cartmate.cartmate.shared.coupon.dto.CouponQuote;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 주문에 쿠폰 쓰기: 할인 계산(전체·판매자 쿠폰), 쓸 수 없는 이유, 사용·복구
class CouponOrderUseCaseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);
    private static final Long MEMBER = 1L;
    private static final Long ORDER = 100L;
    // 판매자 7: 30,000 / 판매자 8: 20,000
    private static final CouponOrderTarget TARGET = new CouponOrderTarget(50_000L, List.of(
            new CouponOrderTarget.SellerAmount(7L, 30_000L),
            new CouponOrderTarget.SellerAmount(8L, 20_000L)));

    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final MemberCouponRepository memberCouponRepository = mock(MemberCouponRepository.class);
    private final CouponOrderUseCase useCase = new CouponOrderUseCase(couponRepository, memberCouponRepository);

    private MemberCoupon given(Coupon coupon, long couponId, long memberCouponId, Long owner) {
        ReflectionTestUtils.setField(coupon, "id", couponId);
        MemberCoupon mc = MemberCoupon.issue(coupon, owner, NOW);
        ReflectionTestUtils.setField(mc, "id", memberCouponId);
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon));
        when(couponRepository.findAllById(any())).thenReturn(List.of(coupon));
        when(memberCouponRepository.findById(memberCouponId)).thenReturn(Optional.of(mc));
        when(memberCouponRepository.findByIdForUpdate(memberCouponId)).thenReturn(Optional.of(mc));
        when(memberCouponRepository.findAllByMemberIdAndStatusNotOrderByIdDesc(owner, MemberCouponStatus.USED)).thenReturn(List.of(mc));
        return mc;
    }

    private static Coupon all(long percent, long minOrder) {
        return Coupon.create("전체 " + percent + "%", CouponDiscountType.RATE, percent, null, minOrder, CouponScope.ALL, null,
                CouponIssueType.DOWNLOAD, null, null, null, null, 30, null, 99L);
    }

    private static Coupon sellerFixed(long sellerId, long amount) {
        return Coupon.create("판매자 쿠폰", CouponDiscountType.FIXED, amount, null, 0, CouponScope.SELLER, sellerId,
                CouponIssueType.DOWNLOAD, null, null, null, null, 30, null, 99L);
    }

    @Test
    void 전체_쿠폰은_주문_금액_기준_할인_플랫폼_부담() {
        given(all(10, 0), 10L, 1L, MEMBER);

        CouponQuote quote = useCase.quote(MEMBER, 1L, "ORDER", ORDER, TARGET, NOW);

        assertThat(quote.discountAmount()).isEqualTo(5_000L);
        assertThat(quote.platformBurden()).isTrue();
        assertThat(quote.sellerId()).isNull();
    }

    @Test
    void 판매자_쿠폰은_그_판매자_상품_금액까지만_할인_판매자_부담() {
        given(sellerFixed(8L, 25_000L), 10L, 1L, MEMBER);

        CouponQuote quote = useCase.quote(MEMBER, 1L, "ORDER", ORDER, TARGET, NOW);

        assertThat(quote.discountAmount()).isEqualTo(20_000L); // 판매자 8 상품 20,000까지만
        assertThat(quote.platformBurden()).isFalse();
        assertThat(quote.sellerId()).isEqualTo(8L);
    }

    @Test
    void 쓸_수_없으면_이유와_함께_거절() {
        given(sellerFixed(9L, 1_000L), 10L, 1L, MEMBER); // 주문에 판매자 9 상품 없음
        assertThatThrownBy(() -> useCase.quote(MEMBER, 1L, "ORDER", ORDER, TARGET, NOW))
                .hasMessageContaining(CouponErrorCode.COUPON_NOT_APPLICABLE.getMsg());
        assertThatThrownBy(() -> useCase.quote(2L, 1L, "ORDER", ORDER, TARGET, NOW)) // 남의 쿠폰
                .hasMessageContaining(CouponErrorCode.COUPON_NOT_OWNED.getMsg());
    }

    @Test
    void 결제_화면_목록은_못_쓰는_쿠폰을_이유와_함께_회색으로() {
        given(all(10, 60_000), 10L, 1L, MEMBER); // 최소 60,000원

        List<CouponOption> options = useCase.options(MEMBER, "ORDER", ORDER, TARGET, NOW);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).usable()).isFalse();
        assertThat(options.get(0).unusableReason()).isEqualTo("60,000원 이상 주문 시 사용 가능");
        assertThat(options.get(0).expectedDiscount()).isZero();
    }

    @Test
    void 사용하면_사용_완료_같은_주문에_다시_써도_같은_결과_다른_주문엔_못_씀() {
        MemberCoupon mc = given(all(10, 0), 10L, 1L, MEMBER);

        useCase.use(MEMBER, 1L, "ORDER", ORDER, TARGET, NOW);
        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.USED);

        assertThat(useCase.use(MEMBER, 1L, "ORDER", ORDER, TARGET, NOW).discountAmount()).isEqualTo(5_000L); // 재시도
        assertThatThrownBy(() -> useCase.use(MEMBER, 1L, "ORDER", 200L, TARGET, NOW))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void 전체_취소면_그_주문에_쓴_쿠폰만_돌아온다() {
        MemberCoupon mc = given(all(10, 0), 10L, 1L, MEMBER);
        useCase.use(MEMBER, 1L, "ORDER", ORDER, TARGET, NOW);

        useCase.restore(MEMBER, 1L, "ORDER", 200L); // 다른 주문
        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.USED);

        useCase.restore(MEMBER, 1L, "ORDER", ORDER);
        assertThat(mc.getStatus()).isEqualTo(MemberCouponStatus.AVAILABLE);
    }
}
