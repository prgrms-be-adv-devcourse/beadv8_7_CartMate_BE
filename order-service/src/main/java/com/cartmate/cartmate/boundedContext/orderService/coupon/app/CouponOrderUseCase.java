package com.cartmate.cartmate.boundedContext.orderService.coupon.app;

import com.cartmate.cartmate.boundedContext.orderService.coupon.domain.Coupon;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 주문에 쿠폰 쓰기: 결제 화면 목록, 할인 계산, 사용, 복구.
 *
 * <p>주문 하나(orderType + orderId)에 쿠폰 한 장. 할인 규칙은 {@link Coupon}에만 있고, 여기선 "쓸 수 있는지"만 판단한다.
 * 쓸 수 없는 이유는 결제 화면에 그대로 보여 줄 문구다.
 */
@Service
@RequiredArgsConstructor
class CouponOrderUseCase {

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;

    // 결제 화면 쿠폰 목록: 아직 안 쓴 내 쿠폰 전부, 쓸 수 있는 것은 예상 할인액, 못 쓰는 것은 이유
    List<CouponOption> options(Long memberId, String orderType, Long orderId, CouponOrderTarget target, LocalDateTime now) {
        List<MemberCoupon> owned = memberCouponRepository.findAllByMemberIdAndStatusNotOrderByIdDesc(memberId, MemberCouponStatus.USED);
        Map<Long, Coupon> coupons = couponRepository.findAllById(owned.stream().map(MemberCoupon::getCouponId).distinct().toList())
                .stream().collect(Collectors.toMap(Coupon::getId, Function.identity()));
        return owned.stream()
                .filter(mc -> coupons.containsKey(mc.getCouponId()))
                .map(mc -> toOption(mc, coupons.get(mc.getCouponId()), orderType, orderId, target, now))
                .toList();
    }

    // 할인 계산만 (미리보기·결제 전 확인). 쓸 수 없으면 이유를 담은 예외
    CouponQuote quote(Long memberId, Long memberCouponId, String orderType, Long orderId, CouponOrderTarget target, LocalDateTime now) {
        MemberCoupon memberCoupon = memberCouponRepository.findById(memberCouponId)
                .orElseThrow(() -> new ServiceException(CouponErrorCode.COUPON_NOT_FOUND));
        return quoteOf(memberCoupon, memberId, orderType, orderId, target, now);
    }

    // 주문에 쓰기 (주문의 결제 반영과 같은 트랜잭션). 잠근 뒤 다시 확인하고 사용 예정 → 사용 완료.
    // 같은 주문에 다시 부르면 같은 결과 (재시도 안전)
    CouponQuote use(Long memberId, Long memberCouponId, String orderType, Long orderId, CouponOrderTarget target, LocalDateTime now) {
        MemberCoupon memberCoupon = memberCouponRepository.findByIdForUpdate(memberCouponId)
                .orElseThrow(() -> new ServiceException(CouponErrorCode.COUPON_NOT_FOUND));
        CouponQuote quote = quoteOf(memberCoupon, memberId, orderType, orderId, target, now);
        memberCoupon.reserve(orderType, orderId, now);
        memberCoupon.use(orderType, orderId, now);
        memberCouponRepository.save(memberCoupon);
        return quote;
    }

    // 주문 전체 취소: 그 주문에 쓴 쿠폰만 보유로 돌린다 (다른 주문에 쓴 쿠폰이면 그대로)
    void restore(Long memberId, Long memberCouponId, String orderType, Long orderId) {
        memberCouponRepository.findByIdForUpdate(memberCouponId)
                .filter(mc -> mc.getMemberId().equals(memberId))
                .ifPresent(mc -> {
                    mc.restore(orderType, orderId);
                    memberCouponRepository.save(mc);
                });
    }

    private CouponQuote quoteOf(MemberCoupon memberCoupon, Long memberId, String orderType, Long orderId,
                                CouponOrderTarget target, LocalDateTime now) {
        if (!memberCoupon.getMemberId().equals(memberId)) {
            throw new ServiceException(CouponErrorCode.COUPON_NOT_OWNED);
        }
        Coupon coupon = couponRepository.findById(memberCoupon.getCouponId())
                .orElseThrow(() -> new ServiceException(CouponErrorCode.COUPON_NOT_FOUND));
        CouponErrorCode problem = problemOf(memberCoupon, coupon, orderType, orderId, target, now);
        if (problem != null) {
            throw new ServiceException(problem);
        }
        long discount = coupon.discountFor(coupon.eligibleAmount(target));
        return new CouponQuote(memberCoupon.getId(), coupon.getId(), coupon.getName(), discount,
                coupon.isPlatformBurden(), coupon.getScope() == CouponScope.SELLER ? coupon.getSellerId() : null);
    }

    // 쓸 수 없는 이유 (없으면 null). 순서: 이미 씀 → 다른 주문에 사용 중 → 기간 → 대상 상품 → 최소 금액
    private CouponErrorCode problemOf(MemberCoupon memberCoupon, Coupon coupon, String orderType, Long orderId,
                                      CouponOrderTarget target, LocalDateTime now) {
        boolean usedForThisOrder = orderType.equals(memberCoupon.getUsedOrderType()) && orderId.equals(memberCoupon.getUsedOrderId());
        if (memberCoupon.getStatus() == MemberCouponStatus.USED && !usedForThisOrder) {
            return CouponErrorCode.COUPON_NOT_AVAILABLE;
        }
        if (memberCoupon.getStatus() == MemberCouponStatus.RESERVED && !usedForThisOrder) {
            return CouponErrorCode.COUPON_IN_USE;
        }
        if (memberCoupon.isExpired(now)) {
            return CouponErrorCode.COUPON_EXPIRED;
        }
        long eligible = coupon.eligibleAmount(target);
        if (eligible <= 0) {
            return CouponErrorCode.COUPON_NOT_APPLICABLE;
        }
        if (!coupon.meetsMinimum(eligible)) {
            return CouponErrorCode.COUPON_BELOW_MINIMUM;
        }
        return null;
    }

    private CouponOption toOption(MemberCoupon mc, Coupon c, String orderType, Long orderId, CouponOrderTarget target, LocalDateTime now) {
        CouponErrorCode problem = problemOf(mc, c, orderType, orderId, target, now);
        String reason = problem == null ? null : reasonText(problem, c);
        long expected = problem == null ? c.discountFor(c.eligibleAmount(target)) : 0;
        return new CouponOption(mc.getId(), c.getId(), c.getName(), c.getDiscountType().name(), c.getDiscountValue(),
                c.getMaxDiscountAmount(), c.getMinOrderAmount(), c.getScope().name(), c.getSellerId(), mc.getExpiresAt(),
                problem == null, reason, expected);
    }

    private static String reasonText(CouponErrorCode problem, Coupon coupon) {
        return switch (problem) {
            case COUPON_IN_USE -> "다른 주문에 사용 중이에요";
            case COUPON_EXPIRED -> "사용 기간이 지났어요";
            case COUPON_NOT_APPLICABLE -> "이 판매자 상품에만 쓸 수 있어요";
            case COUPON_BELOW_MINIMUM -> String.format("%,d원 이상 주문 시 사용 가능", coupon.getMinOrderAmount());
            default -> "사용할 수 없는 쿠폰이에요";
        };
    }
}
