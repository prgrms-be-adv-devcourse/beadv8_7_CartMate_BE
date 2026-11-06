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

/**
 * 배송 내부 API. 회원 서버만 부른다(마이페이지 활동 · 탈퇴 가능 여부).
 * 회원 서버가 DeliveryFacade를 직접 부르던 것을 서버 분리에 맞춰 HTTP로 옮긴 것이다. 동작은 Facade 그대로다.
 */
@Hidden
@RestController
@RequestMapping("/internal/deliveries")
@RequiredArgsConstructor
public class DeliveryInternalController {

    private static final int MY_DELIVERY_PAGE_SIZE = 100;

    private final DeliveryFacade deliveryFacade;

    // 회원의 최근 배송 (마이페이지)
    @GetMapping
    public RsData<List<DeliveryItem>> getMyDeliveries(@RequestParam Long memberId) {
        List<DeliveryItem> items = deliveryFacade.getMyDeliveries(memberId, PageRequest.of(0, MY_DELIVERY_PAGE_SIZE))
                .getContent().stream()
                .map(d -> new DeliveryItem(d.orderId(), d.purchaseType().name(), d.status().name(), d.createdAt()))
                .toList();
        return new RsData<>("200-1", "배송 목록", items);
    }

    // 구매자로서 구매확정 전 배송 수
    @GetMapping("/unfinished-count")
    public RsData<Long> countUnfinishedOfBuyer(@RequestParam Long memberId) {
        return new RsData<>("200-1", "진행 중 배송 수", deliveryFacade.countUnfinishedOfBuyer(memberId));
    }

    // 확정된 공동 주문들의 구매확정 전 배송 수
    @GetMapping("/unfinished-count/shared-orders")
    public RsData<Long> countUnfinishedOfSharedOrders(@RequestParam List<Long> ids) {
        return new RsData<>("200-1", "공동 주문 진행 중 배송 수", deliveryFacade.countUnfinishedOfSharedOrders(ids));
    }

    // 구매자로서 처리 중인 교환 · 반품 수
    @GetMapping("/claims-in-progress-count")
    public RsData<Long> countClaimsInProgressOfBuyer(@RequestParam Long memberId) {
        return new RsData<>("200-1", "처리 중 클레임 수", deliveryFacade.countClaimsInProgressOfBuyer(memberId));
    }

    // 판매자로서 발송 · 구매확정 전 배송 수
    @GetMapping("/seller/unfinished-count")
    public RsData<Long> countUnfinishedOfSeller(@RequestParam Long sellerId) {
        return new RsData<>("200-1", "판매자 진행 중 배송 수", deliveryFacade.countUnfinishedOfSeller(sellerId));
    }

    // 판매자로서 처리 중인 교환 · 반품 수
    @GetMapping("/seller/claims-in-progress-count")
    public RsData<Long> countClaimsInProgressOfSeller(@RequestParam Long sellerId) {
        return new RsData<>("200-1", "판매자 처리 중 클레임 수", deliveryFacade.countClaimsInProgressOfSeller(sellerId));
    }

    public record DeliveryItem(Long orderId, String purchaseType, String status, LocalDateTime createdAt) {
    }
}
