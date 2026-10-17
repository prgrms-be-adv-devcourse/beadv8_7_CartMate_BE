package com.cartmate.cartmate.boundedContext.delivery.in;

import com.cartmate.cartmate.boundedContext.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.delivery.app.dto.DeliveryResponse;
import com.cartmate.cartmate.global.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryFacade deliveryFacade;

    @GetMapping("/me")
    public Page<DeliveryResponse> getMyDeliveries(
            @AuthenticationPrincipal SecurityUser user,
            @PageableDefault(
                    size = 20,
                    sort = {"createdAt","id"},
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        Long memberId = user.memberId();

        return deliveryFacade.getMyDeliveries(memberId, pageable);
    }
}
