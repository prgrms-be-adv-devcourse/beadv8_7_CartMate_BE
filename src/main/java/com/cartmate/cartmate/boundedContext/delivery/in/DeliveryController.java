package com.cartmate.cartmate.boundedContext.delivery.in;

import com.cartmate.cartmate.boundedContext.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryDetailResponse;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryFacade deliveryFacade;

    @GetMapping("/me")
    public RsData<Page<DeliveryResponse>> getMyDeliveries(
            @AuthenticationPrincipal SecurityUser user,
            @PageableDefault(
                    size = 20,
                    sort = {"createdAt","id"},
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        Long memberId = user.memberId();

        return new RsData<>("200-1", "배송 목록 조회 성공했습니다.", deliveryFacade.getMyDeliveries(memberId, pageable));
    }

    @GetMapping("/{deliveryId}")
    public RsData<DeliveryDetailResponse> getDeliveryDetails(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long deliveryId
    ) {
        return new RsData<>("200-1", "배송 상세 조회 성공했습니다.", deliveryFacade.getDeliveryDetails(deliveryId, user.memberId()));
    }
}
