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
 * 회원이 가진 쿠폰. 같은 쿠폰은 회원당 한 장 (유니크 제약 = 중복 발급 방지, 자동 발급이 두 번 와도 한 장).
 *
 * <p>상태: 보유 → 사용 예정(결제 전 주문에 선택) → 사용 완료(결제 끝). 결제 전 취소·전체 취소면 보유로 돌아간다.
 * 기간 만료는 저장하지 않고 {@link #expiresAt}으로 판단한다 (스케줄러 없이).
 */
@Entity
@Table(name = "member_coupon",
        uniqueConstraints = @UniqueConstraint(name = "uk_member_coupon", columnNames = {"coupon_id", "member_id"}),
        indexes = @Index(name = "idx_member_coupon_member", columnList = "member_id, status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberCoupon extends BaseIdAndTime {

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private MemberCouponStatus status;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // 사용 예정·사용 완료일 때 어느 주문에 썼는지 (주문 구분 + 주문 ID)
    @Column(name = "used_order_type", length = 30)
    private String usedOrderType;

    @Column(name = "used_order_id")
    private Long usedOrderId;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public static MemberCoupon issue(Coupon coupon, Long memberId, LocalDateTime now) {
        MemberCoupon memberCoupon = new MemberCoupon();
        memberCoupon.couponId = coupon.getId();
        memberCoupon.memberId = memberId;
        memberCoupon.status = MemberCouponStatus.AVAILABLE;
        memberCoupon.issuedAt = now;
        memberCoupon.expiresAt = coupon.expiresAtFor(now);
        return memberCoupon;
    }

    // 만료 시각이 되는 순간부터 못 쓴다 (만료 시각 = "그 전까지" 쓸 수 있는 경계, 보통 마지막 날 다음 날 0시)
    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    // 화면에 보일 상태: 보유인데 기간이 지났으면 만료
    public String displayStatus(LocalDateTime now) {
        if (status == MemberCouponStatus.AVAILABLE && isExpired(now)) {
            return "EXPIRED";
        }
        return status.name();
    }

    // ===== 주문 연결 (1-b에서 결제 흐름이 부른다) =====

    // 결제 전 주문에 고름. 같은 주문에 다시 고르면 그대로(멱등)
    public void reserve(String orderType, Long orderId, LocalDateTime now) {
        if (status == MemberCouponStatus.RESERVED && isSameOrder(orderType, orderId)) {
            return;
        }
        if (status != MemberCouponStatus.AVAILABLE) {
            throw new ServiceException(CouponErrorCode.COUPON_NOT_AVAILABLE);
        }
        if (isExpired(now)) {
            throw new ServiceException(CouponErrorCode.COUPON_EXPIRED);
        }
        this.status = MemberCouponStatus.RESERVED;
        this.usedOrderType = orderType;
        this.usedOrderId = orderId;
    }

    // 결제 끝. 같은 주문이면 다시 불러도 그대로(멱등)
    public void use(String orderType, Long orderId, LocalDateTime now) {
        if (status == MemberCouponStatus.USED && isSameOrder(orderType, orderId)) {
            return;
        }
        if (status != MemberCouponStatus.RESERVED || !isSameOrder(orderType, orderId)) {
            throw new ServiceException(CouponErrorCode.COUPON_NOT_AVAILABLE);
        }
        this.status = MemberCouponStatus.USED;
        this.usedAt = now;
    }

    // 결제 전 취소·전체 취소. 이 주문에 쓴 쿠폰일 때만 돌려준다 (다른 주문이면 그대로)
    public void restore(String orderType, Long orderId) {
        if (status == MemberCouponStatus.AVAILABLE || !isSameOrder(orderType, orderId)) {
            return;
        }
        this.status = MemberCouponStatus.AVAILABLE;
        this.usedOrderType = null;
        this.usedOrderId = null;
        this.usedAt = null;
    }

    private boolean isSameOrder(String orderType, Long orderId) {
        return orderType.equals(usedOrderType) && orderId.equals(usedOrderId);
    }
}
