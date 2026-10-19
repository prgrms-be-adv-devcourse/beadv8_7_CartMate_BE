package com.cartmate.cartmate.boundedContext.cart.in.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseCreateResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseDashboardResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseDeadlineUpdateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseDetailResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseProductAddRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupindividual.IndividualGroupPurchaseSummaryResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/group-purchases/individual")
@RequiredArgsConstructor
public class IndividualGroupPurchaseController {

    private final IndividualGroupPurchaseFacade individualGroupPurchaseFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<IndividualGroupPurchaseCreateResponse> create(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody IndividualGroupPurchaseCreateRequest request
    ) {
        return new RsData<>("201-1", "공동구매를 만들었습니다.", individualGroupPurchaseFacade.create(user.memberId(), request));
    }

    @GetMapping("/me")
    public RsData<List<IndividualGroupPurchaseSummaryResponse>> getMyList(
            @AuthenticationPrincipal SecurityUser user
    ) {
        return new RsData<>("200-1", "내 공동구매 목록 조회 성공했습니다.", individualGroupPurchaseFacade.getMyList(user.memberId()));
    }

    @GetMapping("/invite/{inviteCode}")
    public RsData<IndividualGroupPurchaseDetailResponse> getByInviteCode(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable @Pattern(regexp = "^[a-z0-9]{12}$") String inviteCode
    ) {
        return new RsData<>("200-1", "공동구매 조회 성공했습니다.", individualGroupPurchaseFacade.getByInviteCode(user.memberId(), inviteCode));
    }

    @GetMapping("/{id}")
    public RsData<IndividualGroupPurchaseDetailResponse> getDetail(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        return new RsData<>("200-1", "공동구매 조회 성공했습니다.", individualGroupPurchaseFacade.getDetail(user.memberId(), id));
    }

    @GetMapping("/{id}/dashboard")
    public RsData<IndividualGroupPurchaseDashboardResponse> getDashboard(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        return new RsData<>("200-1", "현황판 조회 성공했습니다.", individualGroupPurchaseFacade.getDashboard(user.memberId(), id));
    }

    @PostMapping("/{id}/products")
    public RsData<Void> addProduct(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id,
            @Valid @RequestBody IndividualGroupPurchaseProductAddRequest request
    ) {
        individualGroupPurchaseFacade.addProduct(user.memberId(), id, request.productId());
        return new RsData<>("200-1", "판매 상품을 추가했습니다.");
    }

    @DeleteMapping("/{id}/products/{productId}")
    public RsData<Void> removeProduct(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id,
            @PathVariable Long productId
    ) {
        individualGroupPurchaseFacade.removeProduct(user.memberId(), id, productId);
        return new RsData<>("200-1", "판매 상품을 뺐습니다.");
    }

    @PatchMapping("/{id}/deadline")
    public RsData<Void> changeDeadline(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id,
            @Valid @RequestBody IndividualGroupPurchaseDeadlineUpdateRequest request
    ) {
        individualGroupPurchaseFacade.changeDeadline(user.memberId(), id, request.deadlineAt());
        return new RsData<>("200-1", "판매 마감 시각을 변경했습니다.");
    }

    @PostMapping("/{id}/close")
    public RsData<Void> close(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        individualGroupPurchaseFacade.close(user.memberId(), id);
        return new RsData<>("200-1", "공동구매를 마감했습니다.");
    }
}
