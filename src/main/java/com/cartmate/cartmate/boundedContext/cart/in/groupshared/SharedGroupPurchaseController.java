package com.cartmate.cartmate.boundedContext.cart.in.groupshared;

import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseDetailResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseItemAddRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseItemOptionUpdateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseItemQuantityUpdateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseJoinRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseMaxParticipantsUpdateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseSummaryResponse;
import com.cartmate.cartmate.boundedContext.cart.app.groupshared.SharedGroupPurchaseFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/group-purchases/shared")
@RequiredArgsConstructor
public class SharedGroupPurchaseController {


    private static final String MEMBER_ID_HEADER = "X-Member-Id";

    private static final String BASE_PATH = "/group-purchases/shared/";

    private final SharedGroupPurchaseFacade sharedGroupPurchaseFacade;

    @PostMapping
    public ResponseEntity<SharedGroupPurchaseCreateResponse> create(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @Valid @RequestBody SharedGroupPurchaseCreateRequest request
    ) {
        SharedGroupPurchaseCreateResponse response = sharedGroupPurchaseFacade.create(memberId, request);
        return ResponseEntity.created(URI.create(BASE_PATH + response.id())).body(response);
    }


    @GetMapping("/me")
    public ResponseEntity<List<SharedGroupPurchaseSummaryResponse>> getMyList(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId
    ) {
        return ResponseEntity.ok(sharedGroupPurchaseFacade.getMyList(memberId));
    }


    @GetMapping("/{id}")
    public ResponseEntity<SharedGroupPurchaseDetailResponse> getDetail(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(sharedGroupPurchaseFacade.getDetail(memberId, id));
    }



    @PostMapping("/join")
    public ResponseEntity<SharedGroupPurchaseDetailResponse> join(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @Valid @RequestBody SharedGroupPurchaseJoinRequest request
    ) {
        Long id = sharedGroupPurchaseFacade.join(memberId, request.inviteCode());
        return ResponseEntity.ok(sharedGroupPurchaseFacade.getDetail(memberId, id));
    }


    @DeleteMapping("/{id}/members/me")
    public ResponseEntity<Void> leave(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id
    ) {
        sharedGroupPurchaseFacade.leave(memberId, id);
        return ResponseEntity.noContent().build();
    }


    @DeleteMapping("/{id}/members/{targetMemberId}")
    public ResponseEntity<Void> kick(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id,
            @PathVariable Long targetMemberId
    ) {
        sharedGroupPurchaseFacade.kick(memberId, id, targetMemberId);
        return ResponseEntity.noContent().build();
    }


    @PatchMapping("/{id}/max-participants")
    public ResponseEntity<Void> increaseMaxParticipants(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody SharedGroupPurchaseMaxParticipantsUpdateRequest request
    ) {
        sharedGroupPurchaseFacade.increaseMaxParticipants(memberId, id, request.maxParticipants());
        return ResponseEntity.noContent().build();
    }


    @PostMapping("/{id}/items")
    public ResponseEntity<Void> addItem(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id,
            @Valid @RequestBody SharedGroupPurchaseItemAddRequest request
    ) {
        sharedGroupPurchaseFacade.addItem(memberId, id, request.inventoryId(), request.quantity());
        return ResponseEntity.noContent().build();
    }


    @PatchMapping("/{id}/items/{itemId}/quantity")
    public ResponseEntity<Void> changeItemQuantity(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Valid @RequestBody SharedGroupPurchaseItemQuantityUpdateRequest request
    ) {
        sharedGroupPurchaseFacade.changeItemQuantity(memberId, id, itemId, request.quantity());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/items/{itemId}/option")
    public ResponseEntity<Void> changeItemOption(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Valid @RequestBody SharedGroupPurchaseItemOptionUpdateRequest request
    ) {
        sharedGroupPurchaseFacade.changeItemOption(memberId, id, itemId, request.inventoryId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<Void> removeItem(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id,
            @PathVariable Long itemId
    ) {
        sharedGroupPurchaseFacade.removeItem(memberId, id, itemId);
        return ResponseEntity.noContent().build();
    }


    @PostMapping("/{id}/close")
    public ResponseEntity<Void> close(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id
    ) {
        sharedGroupPurchaseFacade.close(memberId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id
    ) {
        sharedGroupPurchaseFacade.cancel(memberId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<SharedGroupPurchaseCreateResponse> duplicate(
            @RequestHeader(MEMBER_ID_HEADER) Long memberId,
            @PathVariable Long id
    ) {
        SharedGroupPurchaseCreateResponse response = sharedGroupPurchaseFacade.duplicate(memberId, id);
        return ResponseEntity.created(URI.create(BASE_PATH + response.id())).body(response);
    }
}
