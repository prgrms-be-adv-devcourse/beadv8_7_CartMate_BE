package com.cartmate.cartmate.boundedContext.cart.in.personal;

import com.cartmate.cartmate.boundedContext.cart.app.personal.CartFacade;
import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartItemAddRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartItemQuantityUpdateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartFacade cartFacade;

    @GetMapping
    public RsData<CartResponse> getCart(@AuthenticationPrincipal SecurityUser user) {
        return new RsData<>("200-1", "장바구니 조회 성공했습니다.", cartFacade.getCart(user.memberId()));
    }

    @PostMapping("/items")
    public RsData<Void> addItem(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody CartItemAddRequest request
    ) {
        cartFacade.addItem(user.memberId(), request.inventoryId(), request.quantity());
        return new RsData<>("200-1", "장바구니에 담았습니다.");
    }

    @PatchMapping("/items/{cartItemId}")
    public RsData<Void> changeQuantity(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long cartItemId,
            @Valid @RequestBody CartItemQuantityUpdateRequest request
    ) {
        cartFacade.changeQuantity(user.memberId(), cartItemId, request.quantity());
        return new RsData<>("200-1", "수량을 변경했습니다.");
    }

    @DeleteMapping("/items/{cartItemId}")
    public RsData<Void> removeItem(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long cartItemId
    ) {
        cartFacade.removeItem(user.memberId(), cartItemId);
        return new RsData<>("200-1", "장바구니에서 삭제했습니다.");
    }

    @DeleteMapping("/items")
    public RsData<Void> clear(@AuthenticationPrincipal SecurityUser user) {
        cartFacade.clear(user.memberId());
        return new RsData<>("200-1", "장바구니를 비웠습니다.");
    }
}
