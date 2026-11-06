package com.cartmate.cartmate.boundedContext.etcService.delivery.in.internal;

import com.cartmate.cartmate.boundedContext.etcService.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Hidden
@RestController
@RequestMapping("/internal/deliveries")
@RequiredArgsConstructor
public class DeliveryInternalController {

    private static final int MY_DELIVERY_PAGE_SIZE = 100;

    private final DeliveryFacade deliveryFacade;

    @GetMapping
    public RsData<List<DeliveryItem>> getMyDeliveries(@RequestParam Long memberId) {
        List<DeliveryItem> items = deliveryFacade.getMyDeliveries(memberId, PageRequest.of(0, MY_DELIVERY_PAGE_SIZE))
                .getContent().stream()
                .map(d -> new DeliveryItem(d.orderId(), d.purchaseType().name(), d.status().name(), d.createdAt()))
                .toList();
        return new RsData<>("200-1", "배송 목록", items);
    }

    @GetMapping("/unfinished-count")
    public RsData<Long> countUnfinishedOfBuyer(@RequestParam Long memberId) {
        return new RsData<>("200-1", "진행 중 배송 수", deliveryFacade.countUnfinishedOfBuyer(memberId));
    }

    @GetMapping("/unfinished-count/shared-orders")
    public RsData<Long> countUnfinishedOfSharedOrders(@RequestParam List<Long> ids) {
        return new RsData<>("200-1", "공동 주문 진행 중 배송 수", deliveryFacade.countUnfinishedOfSharedOrders(ids));
    }

    @GetMapping("/claims-in-progress-count")
    public RsData<Long> countClaimsInProgressOfBuyer(@RequestParam Long memberId) {
        return new RsData<>("200-1", "처리 중 클레임 수", deliveryFacade.countClaimsInProgressOfBuyer(memberId));
    }

    @GetMapping("/seller/unfinished-count")
    public RsData<Long> countUnfinishedOfSeller(@RequestParam Long sellerId) {
        return new RsData<>("200-1", "판매자 진행 중 배송 수", deliveryFacade.countUnfinishedOfSeller(sellerId));
    }

    @GetMapping("/seller/claims-in-progress-count")
    public RsData<Long> countClaimsInProgressOfSeller(@RequestParam Long sellerId) {
        return new RsData<>("200-1", "판매자 처리 중 클레임 수", deliveryFacade.countClaimsInProgressOfSeller(sellerId));
    }

    public record DeliveryItem(Long orderId, String purchaseType, String status, LocalDateTime createdAt) {
    }
}
