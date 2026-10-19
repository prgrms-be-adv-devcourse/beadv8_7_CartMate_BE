package com.cartmate.cartmate.boundedContext.order.in.groupshared;

import com.cartmate.cartmate.boundedContext.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.order.dto.groupshared.SharedGroupOrderDetailResponse;
import com.cartmate.cartmate.boundedContext.order.dto.groupshared.SharedGroupOrderSummaryResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/group-orders/shared")
@RequiredArgsConstructor
public class SharedGroupOrderController {

    private final SharedGroupOrderFacade sharedGroupOrderFacade;

    @GetMapping("/me")
    public RsData<List<SharedGroupOrderSummaryResponse>> getMyList(@AuthenticationPrincipal SecurityUser user) {
        return new RsData<>("200-1", "내 공동 주문 목록 조회 성공했습니다.", sharedGroupOrderFacade.getMyList(user.memberId()));
    }

    @GetMapping("/{id}")
    public RsData<SharedGroupOrderDetailResponse> getDetail(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        return new RsData<>("200-1", "공동 주문 조회 성공했습니다.", sharedGroupOrderFacade.getDetail(user.memberId(), id));
    }

    @PostMapping("/{id}/cancel")
    public RsData<Void> cancel(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        sharedGroupOrderFacade.cancelByCreator(user.memberId(), id);
        return new RsData<>("200-1", "공동 주문을 취소했습니다.");
    }
}
