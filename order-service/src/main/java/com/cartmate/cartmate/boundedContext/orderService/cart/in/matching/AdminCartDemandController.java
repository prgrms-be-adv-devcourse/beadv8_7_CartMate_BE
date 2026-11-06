package com.cartmate.cartmate.boundedContext.orderService.cart.in.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.app.matching.CartMatchingFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupindividual.IndividualGroupPurchaseCreateResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching.DemandCandidateResponse;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching.DemandGroupPurchaseOpenRequest;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/carts/demands")
@RequiredArgsConstructor
public class AdminCartDemandController {

    private final CartMatchingFacade cartMatchingFacade;
    private final IndividualGroupPurchaseFacade individualGroupPurchaseFacade;

    @GetMapping
    public RsData<List<DemandCandidateResponse>> getCandidates(
            @RequestParam(defaultValue = "3") int minExactMembers,
            @RequestParam(defaultValue = "5") int minMembers,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "14") int cooldownDays
    ) {
        return new RsData<>("200-1", "공동구매 후보를 조회했습니다.",
                cartMatchingFacade.getDemandCandidates(minExactMembers, minMembers, days, size, cooldownDays));
    }

    @PostMapping("/{productId}/group-purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<IndividualGroupPurchaseCreateResponse> openGroupPurchase(
            @AuthenticationPrincipal SecurityUser admin,
            @PathVariable Long productId,
            @Valid @RequestBody DemandGroupPurchaseOpenRequest request
    ) {
        return new RsData<>("201-1", "수요 공동구매를 열었습니다.",
                individualGroupPurchaseFacade.openFromDemand(admin.memberId(), productId, request));
    }
}
