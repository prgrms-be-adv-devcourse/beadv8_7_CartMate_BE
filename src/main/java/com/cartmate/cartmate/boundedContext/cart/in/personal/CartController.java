package com.cartmate.cartmate.boundedContext.cart.in.personal;

import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartItemAddRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartItemQuantityUpdateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.personal.CartResponse;
import com.cartmate.cartmate.boundedContext.cart.app.personal.CartFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/carts")
@RequiredArgsConstructor
public class CartController {

    private static final String MEMBER_ID_HEADER = "X-Member-Id";

    private final CartFacade cartFacade;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(@RequestHeader(MEMBER_ID_HEADER) Long memberId) {
        return ResponseEntity.ok(cartFacade.getCart(memberId));
    }

    @PostMapping("/items")
    public ResponseEntity<Void> addItem(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @Valid @RequestBody CartItemAddRequest request
    ) {
        cartFacade.addItem(memberId, request.inventoryId(), request.quantity());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<Void> changeQuantity(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long cartItemId,
            @Valid @RequestBody CartItemQuantityUpdateRequest request
    ) {
        cartFacade.changeQuantity(memberId, cartItemId, request.quantity());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<Void> removeItem(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long cartItemId
    ) {
        cartFacade.removeItem(memberId, cartItemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/items")
    public ResponseEntity<Void> clear(@RequestHeader(MEMBER_ID_HEADER) Long memberId) {
        cartFacade.clear(memberId);
        return ResponseEntity.noContent().build();
    }
}
