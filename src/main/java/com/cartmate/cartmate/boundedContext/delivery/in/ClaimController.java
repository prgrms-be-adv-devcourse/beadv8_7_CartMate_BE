package com.cartmate.cartmate.boundedContext.delivery.in;

import com.cartmate.cartmate.boundedContext.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimCreateRequest;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryClaimResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final DeliveryFacade deliveryFacade;

    // 내가 접수한 교환·환불 목록과 진행 상태를 최신순으로 조회한다.
    @GetMapping("/me")
    public RsData<Page<DeliveryClaimResponse>> getMyClaims(
            @AuthenticationPrincipal SecurityUser user,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return new RsData<>("200-1", "내 교환·환불 목록 조회 성공했습니다.",
                deliveryFacade.getMyClaims(user.memberId(), pageable));
    }

    // 수령인(구매자) 본인이 배송완료된 배송에서 상품을 골라 교환·환불을 접수한다.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<DeliveryClaimResponse> createClaim(
            @AuthenticationPrincipal SecurityUser user,
            @RequestBody DeliveryClaimCreateRequest request
    ) {
        return new RsData<>("201-1", "교환·환불이 접수되었습니다.",
                deliveryFacade.createClaim(request.toCommand(user.memberId())));
    }
}
