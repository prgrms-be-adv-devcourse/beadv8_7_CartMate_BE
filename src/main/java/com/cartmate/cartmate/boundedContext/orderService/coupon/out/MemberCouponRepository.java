package com.cartmate.cartmate.boundedContext.orderService.coupon.out;

import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.MemberCoupon;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.MemberCouponStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MemberCouponRepository extends JpaRepository<MemberCoupon, Long> {

    boolean existsByCouponIdAndMemberId(Long couponId, Long memberId);

    List<MemberCoupon> findAllByMemberIdOrderByIdDesc(Long memberId);

    // 다운로드 목록에서 "이미 받음" 표시용
    @Query("select mc.couponId from MemberCoupon mc where mc.memberId = :memberId and mc.couponId in :couponIds")
    List<Long> findOwnedCouponIds(@Param("memberId") Long memberId, @Param("couponIds") Collection<Long> couponIds);

    // 관리자 목록: 쿠폰별 사용 완료 수 (쿠폰 수만큼 쿼리하지 않고 한 번에)
    @Query("select mc.couponId, count(mc) from MemberCoupon mc " +
            "where mc.couponId in :couponIds and mc.status = :status group by mc.couponId")
    List<Object[]> countByCouponIdsAndStatus(@Param("couponIds") Collection<Long> couponIds,
                                             @Param("status") MemberCouponStatus status);
}