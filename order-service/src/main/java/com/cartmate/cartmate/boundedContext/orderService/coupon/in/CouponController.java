package com.cartmate.cartmate.boundedContext.orderService.coupon.in;

import com.cartmate.cartmate.boundedContext.orderService.coupon.app.CouponFacade;
import com.cartmate.cartmate.boundedContext.orderService.coupon.dto.DownloadableCouponResponse;
import com.cartmate.cartmate.boundedContext.orderService.coupon.dto.MemberCouponResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponFacade couponFacade;

    @GetMapping("/me")
    @Operation(operationId = "getMyCoupons", summary = "내 쿠폰함")
    public RsData<List<MemberCouponResponse>> myCoupons(@AuthenticationPrincipal SecurityUser user) {
        return new RsData<>("200-1", "쿠폰함 조회 성공", couponFacade.myCoupons(user.memberId()));
    }

    @GetMapping("/downloadable")
    @Operation(operationId = "getDownloadableCoupons", summary = "받을 수 있는 쿠폰 (sellerId를 주면 전체 + 그 판매자 쿠폰)")
    public RsData<List<DownloadableCouponResponse>> downloadable(@AuthenticationPrincipal SecurityUser user,
                                                                 @RequestParam(required = false) Long sellerId) {
        return new RsData<>("200-1", "받을 수 있는 쿠폰 조회 성공", couponFacade.downloadable(user.memberId(), sellerId));
    }

    @PostMapping("/{couponId}/download")
    @Operation(operationId = "downloadCoupon", summary = "쿠폰 받기")
    public RsData<Void> download(@AuthenticationPrincipal SecurityUser user, @PathVariable Long couponId) {
        couponFacade.download(user.memberId(), couponId);
        return new RsData<>("200-1", "쿠폰을 받았습니다.");
    }
}
