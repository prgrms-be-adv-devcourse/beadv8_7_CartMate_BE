package com.cartmate.cartmate.domain.cart.controller;

import com.cartmate.cartmate.domain.cart.dto.CartItemAddRequest;
import com.cartmate.cartmate.domain.cart.dto.CartItemQuantityUpdateRequest;
import com.cartmate.cartmate.domain.cart.dto.CartResponse;
import com.cartmate.cartmate.domain.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/carts")
@RequiredArgsConstructor
public class CartController {

    // TODO: 인증 적용 후 로그인 회원 정보로 교체
    private static final String MEMBER_ID_HEADER = "X-Member-Id";

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(@RequestHeader(MEMBER_ID_HEADER) Long memberId) {
        return ResponseEntity.ok(cartService.getCart(memberId));
    }

    @PostMapping("/items")
    public ResponseEntity<Void> addItem(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @Valid @RequestBody CartItemAddRequest request
    ) {
        cartService.addItem(memberId, request.inventoryId(), request.quantity());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<Void> changeQuantity(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long cartItemId,
            @Valid @RequestBody CartItemQuantityUpdateRequest request
    ) {
        cartService.changeQuantity(memberId, cartItemId, request.quantity());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<Void> removeItem(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long cartItemId
    ) {
        cartService.removeItem(memberId, cartItemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/items")
    public ResponseEntity<Void> clear(@RequestHeader(MEMBER_ID_HEADER) Long memberId) {
        cartService.clear(memberId);
        return ResponseEntity.noContent().build();
    }
}
