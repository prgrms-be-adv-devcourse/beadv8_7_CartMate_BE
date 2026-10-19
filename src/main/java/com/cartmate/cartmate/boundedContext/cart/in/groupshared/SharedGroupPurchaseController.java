package com.cartmate.cartmate.boundedContext.cart.in.groupshared;

import com.cartmate.cartmate.boundedContext.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseDetailResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseItemAddRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseItemOptionUpdateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseItemQuantityUpdateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseJoinRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseMaxParticipantsUpdateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseSummaryResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/group-purchases/shared")
@RequiredArgsConstructor
public class SharedGroupPurchaseController {

    private final SharedGroupPurchaseFacade sharedGroupPurchaseFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<SharedGroupPurchaseCreateResponse> create(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody SharedGroupPurchaseCreateRequest request
    ) {
        return new RsData<>("201-1", "공동구매를 만들었습니다.", sharedGroupPurchaseFacade.create(user.memberId(), request));
    }

    @GetMapping("/me")
    public RsData<List<SharedGroupPurchaseSummaryResponse>> getMyList(
            @AuthenticationPrincipal SecurityUser user
    ) {
        return new RsData<>("200-1", "참여 중인 공동구매 목록 조회 성공했습니다.", sharedGroupPurchaseFacade.getMyList(user.memberId()));
    }

    @GetMapping("/{id}")
    public RsData<SharedGroupPurchaseDetailResponse> getDetail(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        return new RsData<>("200-1", "공동구매 조회 성공했습니다.", sharedGroupPurchaseFacade.getDetail(user.memberId(), id));
    }

    @PostMapping("/join")
    public RsData<SharedGroupPurchaseDetailResponse> join(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody SharedGroupPurchaseJoinRequest request
    ) {
        Long id = sharedGroupPurchaseFacade.join(user.memberId(), request.inviteCode());
        return new RsData<>("200-1", "공동구매에 참여했습니다.", sharedGroupPurchaseFacade.getDetail(user.memberId(), id));
    }

    @DeleteMapping("/{id}/members/me")
    public RsData<Void> leave(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        sharedGroupPurchaseFacade.leave(user.memberId(), id);
        return new RsData<>("200-1", "공동구매에서 나갔습니다.");
    }

    @DeleteMapping("/{id}/members/{targetMemberId}")
    public RsData<Void> kick(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id,
            @PathVariable Long targetMemberId
    ) {
        sharedGroupPurchaseFacade.kick(user.memberId(), id, targetMemberId);
        return new RsData<>("200-1", "참여자를 내보냈습니다.");
    }

    @PatchMapping("/{id}/max-participants")
    public RsData<Void> increaseMaxParticipants(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id,
            @Valid @RequestBody SharedGroupPurchaseMaxParticipantsUpdateRequest request
    ) {
        sharedGroupPurchaseFacade.increaseMaxParticipants(user.memberId(), id, request.maxParticipants());
        return new RsData<>("200-1", "최대 인원을 변경했습니다.");
    }

    @PostMapping("/{id}/items")
    public RsData<Void> addItem(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id,
            @Valid @RequestBody SharedGroupPurchaseItemAddRequest request
    ) {
        sharedGroupPurchaseFacade.addItem(user.memberId(), id, request.inventoryId(), request.quantity());
        return new RsData<>("200-1", "상품을 담았습니다.");
    }

    @PatchMapping("/{id}/items/{itemId}/quantity")
    public RsData<Void> changeItemQuantity(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Valid @RequestBody SharedGroupPurchaseItemQuantityUpdateRequest request
    ) {
        sharedGroupPurchaseFacade.changeItemQuantity(user.memberId(), id, itemId, request.quantity());
        return new RsData<>("200-1", "수량을 변경했습니다.");
    }

    @PatchMapping("/{id}/items/{itemId}/option")
    public RsData<Void> changeItemOption(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Valid @RequestBody SharedGroupPurchaseItemOptionUpdateRequest request
    ) {
        sharedGroupPurchaseFacade.changeItemOption(user.memberId(), id, itemId, request.inventoryId());
        return new RsData<>("200-1", "옵션을 변경했습니다.");
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public RsData<Void> removeItem(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id,
            @PathVariable Long itemId
    ) {
        sharedGroupPurchaseFacade.removeItem(user.memberId(), id, itemId);
        return new RsData<>("200-1", "상품을 삭제했습니다.");
    }

    @PostMapping("/{id}/close")
    public RsData<Void> close(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        sharedGroupPurchaseFacade.close(user.memberId(), id);
        return new RsData<>("200-1", "공동구매를 마감했습니다.");
    }

    @PostMapping("/{id}/cancel")
    public RsData<Void> cancel(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        sharedGroupPurchaseFacade.cancel(user.memberId(), id);
        return new RsData<>("200-1", "공동구매를 취소했습니다.");
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<SharedGroupPurchaseCreateResponse> duplicate(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        return new RsData<>("201-1", "공동구매를 다시 만들었습니다.", sharedGroupPurchaseFacade.duplicate(user.memberId(), id));
    }
}
