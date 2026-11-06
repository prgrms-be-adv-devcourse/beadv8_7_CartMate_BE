package com.cartmate.cartmate.boundedContext.productService.coupon.in;

import com.cartmate.cartmate.boundedContext.productService.coupon.app.CouponFacade;
import com.cartmate.cartmate.boundedContext.productService.coupon.dto.AdminCouponResponse;
import com.cartmate.cartmate.boundedContext.productService.coupon.dto.CouponCreateRequest;
import com.cartmate.cartmate.global.page.PageResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 관리자 쿠폰 관리 (/api/admin/** 은 관리자만)
@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponFacade couponFacade;

    @PostMapping
    @Operation(operationId = "createCoupon", summary = "쿠폰 만들기")
    public RsData<AdminCouponResponse> create(@AuthenticationPrincipal SecurityUser user,
                                              @Valid @RequestBody CouponCreateRequest request) {
        return new RsData<>("201-1", "쿠폰을 만들었습니다.", couponFacade.create(user.memberId(), request));
    }

    @GetMapping
    @Operation(operationId = "getAdminCoupons", summary = "관리자 쿠폰 목록 (발급 수·사용 수)")
    public RsData<PageResponse<AdminCouponResponse>> list(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return new RsData<>("200-1", "쿠폰 목록 조회 성공", couponFacade.listForAdmin(pageable));
    }

    @PostMapping("/{couponId}/stop")
    @Operation(operationId = "stopCoupon", summary = "쿠폰 발급 중지 (이미 받은 쿠폰은 그대로 사용 가능)")
    public RsData<Void> stop(@PathVariable Long couponId) {
        couponFacade.stop(couponId);
        return new RsData<>("200-1", "쿠폰 발급을 중지했습니다.");
    }
}
