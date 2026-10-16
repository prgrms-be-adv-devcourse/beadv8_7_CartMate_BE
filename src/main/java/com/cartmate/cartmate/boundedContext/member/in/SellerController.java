package com.cartmate.cartmate.boundedContext.member.in;

import com.cartmate.cartmate.boundedContext.member.app.MemberFacade;
import com.cartmate.cartmate.boundedContext.member.app.MemberSellerUseCase;
import com.cartmate.cartmate.boundedContext.member.dto.AvailabilityResponse;
import com.cartmate.cartmate.boundedContext.member.dto.SellerRegisterRequest;
import com.cartmate.cartmate.boundedContext.member.dto.SellerResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.AuthCookieManager;
import com.cartmate.cartmate.global.security.SecurityUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members/me/seller")
@RequiredArgsConstructor
public class SellerController {
    private final MemberFacade memberFacade;
    private final AuthCookieManager authCookieManager;

    // 등록 전이면 data가 null
    @GetMapping
    public RsData<SellerResponse> mySeller(@AuthenticationPrincipal SecurityUser user) {
        return memberFacade.getMySeller(user.memberId())
                .map(s -> new RsData<>("200-1", "판매자 정보 조회 성공", s))
                .orElseGet(() -> new RsData<>("200-2", "등록된 판매자 정보가 없습니다.", null));
    }

    @GetMapping("/check-business-number")
    public RsData<AvailabilityResponse> checkBusinessNumber(@RequestParam String businessNumber) {
        boolean available = memberFacade.isBusinessNumberAvailable(businessNumber);
        return new RsData<>("200-1",
                available ? "사용 가능한 사업자등록번호입니다." : "이미 등록된 사업자등록번호입니다.",
                new AvailabilityResponse(available));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<SellerResponse> register(@AuthenticationPrincipal SecurityUser user,
                                           @Valid @RequestBody SellerRegisterRequest request,
                                           HttpServletResponse response) {
        MemberSellerUseCase.RegisterResult result = memberFacade.registerSeller(user.memberId(), request);
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieManager.accessTokenCookie(result.accessToken()).toString());
        return new RsData<>("201-1", "판매자 등록이 완료되었습니다.", result.seller());
    }
}