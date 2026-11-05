package com.cartmate.cartmate.boundedContext.orderService.cart.in.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupindividual.IndividualGroupPurchaseDashboardResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 어드민이 연 개인물품 공동구매를 만든 어드민이 아니어도 다룬다. /api/admin/** 는 보안 설정에서 ADMIN만 들어온다
@RestController
@RequestMapping("/api/admin/carts/group-purchases/individual")
@RequiredArgsConstructor
public class AdminIndividualGroupPurchaseController {

    private final IndividualGroupPurchaseFacade individualGroupPurchaseFacade;

    @PostMapping("/{id}/close")
    public RsData<Void> close(
            @AuthenticationPrincipal SecurityUser admin,
            @PathVariable Long id
    ) {
        individualGroupPurchaseFacade.closeByAdmin(admin.memberId(), id);
        return new RsData<>("200-1", "공동구매를 마감했습니다.");
    }

    @GetMapping("/{id}/dashboard")
    public RsData<IndividualGroupPurchaseDashboardResponse> getDashboard(@PathVariable Long id) {
        return new RsData<>("200-1", "공동구매 현황을 조회했습니다.",
                individualGroupPurchaseFacade.getDashboardForAdmin(id));
    }
}
