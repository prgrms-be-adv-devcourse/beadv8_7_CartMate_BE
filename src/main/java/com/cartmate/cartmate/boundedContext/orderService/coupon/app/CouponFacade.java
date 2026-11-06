package com.cartmate.cartmate.boundedContext.orderService.coupon.app;

import com.cartmate.cartmate.boundedContext.orderService.coupon.dto.AdminCouponResponse;
import com.cartmate.cartmate.boundedContext.orderService.coupon.dto.CouponCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.coupon.dto.DownloadableCouponResponse;
import com.cartmate.cartmate.boundedContext.orderService.coupon.dto.MemberCouponResponse;
import com.cartmate.cartmate.global.page.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponAdminUseCase couponAdminUseCase;
    private final CouponIssueUseCase couponIssueUseCase;
    private final CouponQueryUseCase couponQueryUseCase;

    // ===== 관리자 =====
    @Transactional
    public AdminCouponResponse create(Long adminId, CouponCreateRequest request) {
        return couponAdminUseCase.create(adminId, request);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminCouponResponse> listForAdmin(Pageable pageable) {
        return couponAdminUseCase.list(pageable);
    }

    @Transactional
    public void stop(Long couponId) {
        couponAdminUseCase.stop(couponId);
    }

    // ===== 회원 =====
    @Transactional
    public void download(Long memberId, Long couponId) {
        couponIssueUseCase.download(couponId, memberId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<MemberCouponResponse> myCoupons(Long memberId) {
        return couponQueryUseCase.myCoupons(memberId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<DownloadableCouponResponse> downloadable(Long memberId, Long sellerId) {
        return couponQueryUseCase.downloadable(memberId, sellerId, LocalDateTime.now());
    }

    // ===== 이벤트 =====
    // 커밋 후 리스너에서 부르므로 새 트랜잭션 (규칙 문서 2-2: REQUIRED면 오류 없이 저장 안 됨)
    public int issueSignupCoupons(Long memberId) {
        return couponIssueUseCase.issueOnSignup(memberId, LocalDateTime.now());
    }
}