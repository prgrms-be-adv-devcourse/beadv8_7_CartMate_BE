package com.cartmate.cartmate.boundedContext.orderService.cart.in.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.matching.CartMatchingFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching.CartMatchResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/carts/matches")
@RequiredArgsConstructor
public class CartMatchController {

    private final CartMatchingFacade cartMatchingFacade;

    @GetMapping
    public RsData<List<CartMatchResponse>> getMyMatches(@AuthenticationPrincipal SecurityUser user) {
        return new RsData<>("200-1", "내 장바구니로 참여할 수 있는 공동구매를 조회했습니다.",
                cartMatchingFacade.getMyMatches(user.memberId()));
    }
}
