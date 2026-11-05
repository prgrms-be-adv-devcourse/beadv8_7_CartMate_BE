package com.cartmate.cartmate.boundedContext.orderService.coupon.app;

import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.Coupon;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.CouponIssueType;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.CouponScope;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.CouponStatus;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.MemberCoupon;
import com.cartmate.cartmate.boundedContext.orderService.coupon.dto.DownloadableCouponResponse;
import com.cartmate.cartmate.boundedContext.orderService.coupon.dto.MemberCouponResponse;
import com.cartmate.cartmate.boundedContext.orderService.coupon.out.CouponRepository;
import com.cartmate.cartmate.boundedContext.orderService.coupon.out.MemberCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class CouponQueryUseCase {

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;

    // 쿠폰함: 최근 받은 순. 상태(사용 가능·사용 예정·사용 완료·기간 만료)는 화면이 탭으로 나눈다
    List<MemberCouponResponse> myCoupons(Long memberId, LocalDateTime now) {
        List<MemberCoupon> owned = memberCouponRepository.findAllByMemberIdOrderByIdDesc(memberId);
        Map<Long, Coupon> coupons = couponRepository.findAllById(owned.stream().map(MemberCoupon::getCouponId).toList())
                .stream().collect(Collectors.toMap(Coupon::getId, Function.identity()));
        return owned.stream()
                .filter(mc -> coupons.containsKey(mc.getCouponId()))
                .map(mc -> MemberCouponResponse.of(mc, coupons.get(mc.getCouponId()), now))
                .toList();
    }

    // 지금 받을 수 있는 다운로드 쿠폰. sellerId를 주면 전체 쿠폰 + 그 판매자 쿠폰만 (판매자 스토어·상품 화면)
    List<DownloadableCouponResponse> downloadable(Long memberId, Long sellerId, LocalDateTime now) {
        List<Coupon> coupons = couponRepository.findAllByIssueTypeAndStatusOrderByIdDesc(CouponIssueType.DOWNLOAD, CouponStatus.ACTIVE)
                .stream()
                .filter(c -> c.isIssuable(now))
                .filter(c -> sellerId == null || c.getScope() == CouponScope.ALL || sellerId.equals(c.getSellerId()))
                .toList();
        Set<Long> owned = memberId == null || coupons.isEmpty()
                ? Set.of()
                : new HashSet<>(memberCouponRepository.findOwnedCouponIds(memberId, coupons.stream().map(Coupon::getId).toList()));
        return coupons.stream().map(c -> DownloadableCouponResponse.of(c, owned.contains(c.getId()))).toList();
    }
}