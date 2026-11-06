package com.cartmate.cartmate.boundedContext.orderService.coupon.domain;

import com.cartmate.cartmate.boundedContext.orderService.coupon.exception.CouponErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 쿠폰(설정). 관리자만 만든다. 같은 쿠폰을 여러 회원이 받고, 회원이 가진 쿠폰은 {@link MemberCoupon}으로 따로 관리한다.
 *
 * <p>할인 규칙(정액·정률·최대 할인·최소 주문 금액)은 이 클래스가 가진다. 할인액을 화면이나 주문이 따로 계산하지 않는다.
 */
@Entity
@Table(name = "coupon", indexes = @Index(name = "idx_coupon_issue", columnList = "issue_type, status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseIdAndTime {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "discount_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private CouponDiscountType discountType;

    // 정액이면 원, 정률이면 % (1~100)
    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    // 정률일 때만. 할인액 상한
    @Column(name = "max_discount_amount")
    private Long maxDiscountAmount;

    // 이 금액 이상일 때만 사용 (공동구매는 분담금 기준). 0이면 조건 없음
    @Column(name = "min_order_amount", nullable = false)
    private long minOrderAmount;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private CouponScope scope;

    // scope = SELLER일 때만
    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "issue_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private CouponIssueType issueType;

    // issueType = AUTO일 때만
    @Column(name = "auto_trigger", length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private CouponAutoTrigger autoTrigger;

    // 발급(다운로드·자동 발급) 가능 기간. 비우면 제한 없음
    @Column(name = "issue_start_at")
    private LocalDateTime issueStartAt;

    @Column(name = "issue_end_at")
    private LocalDateTime issueEndAt;

    // 사용 기간: 정해진 날짜까지(usableUntil) 또는 발급 후 N일(validDays) 중 하나
    @Column(name = "usable_until")
    private LocalDateTime usableUntil;

    @Column(name = "valid_days")
    private Integer validDays;

    // 발급 가능 총 수량. 비우면 제한 없음
    @Column(name = "total_quantity")
    private Integer totalQuantity;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private CouponStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    public static Coupon create(String name, CouponDiscountType discountType, long discountValue, Long maxDiscountAmount,
                                long minOrderAmount, CouponScope scope, Long sellerId,
                                CouponIssueType issueType, CouponAutoTrigger autoTrigger,
                                LocalDateTime issueStartAt, LocalDateTime issueEndAt,
                                LocalDateTime usableUntil, Integer validDays, Integer totalQuantity, Long createdBy) {
        Coupon coupon = new Coupon();
        coupon.name = name;
        coupon.discountType = discountType;
        coupon.discountValue = discountValue;
        coupon.maxDiscountAmount = discountType == CouponDiscountType.RATE ? maxDiscountAmount : null;
        coupon.minOrderAmount = minOrderAmount;
        coupon.scope = scope;
        coupon.sellerId = scope == CouponScope.SELLER ? sellerId : null;
        coupon.issueType = issueType;
        coupon.autoTrigger = issueType == CouponIssueType.AUTO ? autoTrigger : null;
        coupon.issueStartAt = issueStartAt;
        coupon.issueEndAt = issueEndAt;
        coupon.usableUntil = usableUntil;
        coupon.validDays = validDays;
        coupon.totalQuantity = totalQuantity;
        coupon.status = CouponStatus.ACTIVE;
        coupon.createdBy = createdBy;
        coupon.validate();
        return coupon;
    }

    private void validate() {
        if (discountValue <= 0) {
            throw invalid("할인 값은 0보다 커야 합니다.");
        }
        if (discountType == CouponDiscountType.RATE && discountValue > 100) {
            throw invalid("정률 할인은 100%를 넘을 수 없습니다.");
        }
        if (minOrderAmount < 0 || (maxDiscountAmount != null && maxDiscountAmount <= 0)) {
            throw invalid("금액은 0보다 작을 수 없습니다.");
        }
        if (scope == CouponScope.SELLER && sellerId == null) {
            throw invalid("판매자 쿠폰은 판매자를 정해야 합니다.");
        }
        if (issueType == CouponIssueType.AUTO && autoTrigger == null) {
            throw invalid("자동 발급 쿠폰은 발급 조건을 정해야 합니다.");
        }
        if ((usableUntil == null) == (validDays == null)) {
            throw invalid("사용 기간은 '날짜까지'와 '발급 후 N일' 중 하나만 정합니다.");
        }
        if (validDays != null && validDays <= 0) {
            throw invalid("발급 후 사용 일수는 1일 이상이어야 합니다.");
        }
        if (issueStartAt != null && issueEndAt != null && issueEndAt.isBefore(issueStartAt)) {
            throw invalid("발급 종료가 시작보다 빠릅니다.");
        }
        if (totalQuantity != null && totalQuantity <= 0) {
            throw invalid("총 수량은 1 이상이어야 합니다.");
        }
    }

    private static ServiceException invalid(String message) {
        return new ServiceException(CouponErrorCode.COUPON_INVALID.resultCode(), message);
    }

    // ===== 할인 계산 (규칙은 여기 한곳에만) =====

    // 할인 대상 금액에 대한 할인액. 정률은 원 미만 버림, 최대 할인 금액까지, 대상 금액을 넘지 않음
    public long discountFor(long eligibleAmount) {
        if (eligibleAmount <= 0) {
            return 0;
        }
        long discount = discountType == CouponDiscountType.FIXED
                ? discountValue
                : eligibleAmount * discountValue / 100;
        if (maxDiscountAmount != null) {
            discount = Math.min(discount, maxDiscountAmount);
        }
        return Math.min(discount, eligibleAmount);
    }

    public boolean meetsMinimum(long orderAmount) {
        return orderAmount >= minOrderAmount;
    }

    public boolean isPlatformBurden() {
        return scope == CouponScope.ALL;
    }

    // ===== 발급 =====

    public boolean isIssuable(LocalDateTime now) {
        return status == CouponStatus.ACTIVE
                && (issueStartAt == null || !now.isBefore(issueStartAt))
                && (issueEndAt == null || !now.isAfter(issueEndAt))
                && !isSoldOut();
    }

    public boolean isSoldOut() {
        return totalQuantity != null && issuedCount >= totalQuantity;
    }

    // 발급된 쿠폰의 만료 시각: 정해진 날짜, 또는 발급일부터 N일째 날의 끝
    public LocalDateTime expiresAtFor(LocalDateTime issuedAt) {
        if (usableUntil != null) {
            return usableUntil;
        }
        return issuedAt.toLocalDate().plusDays(validDays).atStartOfDay();
    }

    public void stop() {
        this.status = CouponStatus.STOPPED;
    }
}
