package com.cartmate.cartmate.boundedContext.cart.in;

import com.cartmate.cartmate.boundedContext.cart.dto.CartItemAddRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.CartItemQuantityUpdateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.CartResponse;
import com.cartmate.cartmate.boundedContext.cart.app.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/carts")
@RequiredArgsConstructor
public class CartController {

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
