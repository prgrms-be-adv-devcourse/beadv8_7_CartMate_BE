package com.cartmate.cartmate.boundedContext.orderService.coupon.app;

import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.Coupon;
import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.MemberCouponStatus;
import com.cartmate.cartmate.boundedContext.orderService.coupon.dto.AdminCouponResponse;
import com.cartmate.cartmate.boundedContext.orderService.coupon.dto.CouponCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.coupon.exception.CouponErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.coupon.out.CouponRepository;
import com.cartmate.cartmate.boundedContext.orderService.coupon.out.MemberCouponRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.page.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 관리자 쿠폰 관리: 만들기, 목록(발급 수·사용 수), 발급 중지
@Service
@RequiredArgsConstructor
class CouponAdminUseCase {

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;

    AdminCouponResponse create(Long adminId, CouponCreateRequest r) {
        Coupon coupon = Coupon.create(r.name(), r.discountType(), r.discountValue(), r.maxDiscountAmount(),
                r.minOrderAmount() == null ? 0 : r.minOrderAmount(), r.scope(), r.sellerId(),
                r.issueType(), r.autoTrigger(), r.issueStartAt(), r.issueEndAt(),
                r.usableUntil(), r.validDays(), r.totalQuantity(), adminId);
        return AdminCouponResponse.of(couponRepository.save(coupon), 0);
    }

    PageResponse<AdminCouponResponse> list(Pageable pageable) {
        Page<Coupon> page = couponRepository.findAllByOrderByIdDesc(pageable);
        List<Long> ids = page.getContent().stream().map(Coupon::getId).toList();
        Map<Long, Long> usedCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : memberCouponRepository.countByCouponIdsAndStatus(ids, MemberCouponStatus.USED)) {
                usedCounts.put((Long) row[0], (Long) row[1]);
            }
        }
        return PageResponse.of(page.map(c -> AdminCouponResponse.of(c, usedCounts.getOrDefault(c.getId(), 0L))));
    }

    // 중지하면 새로 발급되지 않는다. 이미 받은 쿠폰은 기간 안에 그대로 쓸 수 있다
    void stop(Long couponId) {
        couponRepository.findById(couponId)
                .orElseThrow(() -> new ServiceException(CouponErrorCode.COUPON_NOT_FOUND))
                .stop();
    }
}