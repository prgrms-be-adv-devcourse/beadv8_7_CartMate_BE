package com.cartmate.cartmate.boundedContext.orderService.order.in.internal;

import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 내부 API. 회원 서버만 부른다(마이페이지 활동 · 탈퇴 가능 여부, MSA-6).
 * 응답은 화면용 DTO가 아니라 회원 서버와 맞춘 계약 레코드다 — 화면 DTO를 바꿔도 회원 서버가 깨지지 않게.
 */
@Hidden
@RestController
@RequiredArgsConstructor
public class OrderInternalController {

    private final OrderFacade orderFacade;
    private final SharedGroupOrderFacade sharedGroupOrderFacade;

    // 회원의 개인 · 개인물품 공동구매 주문
    @GetMapping("/internal/orders")
    public RsData<List<OrderItem>> getOrders(@RequestParam Long memberId) {
        List<OrderItem> items = orderFacade.getMyList(memberId).stream()
                .map(o -> new OrderItem(o.id(), o.orderNumber(), o.orderType().name(), o.status().name(),
                        o.totalAmount(), o.itemCount(), o.firstProductName(), o.createdAt()))
                .toList();
        return new RsData<>("200-1", "주문 목록", items);
    }

    // 탈퇴 판단용: 결제 대기 주문 수
    @GetMapping("/internal/orders/payment-pending-count")
    public RsData<Long> countPaymentPending(@RequestParam Long memberId) {
        return new RsData<>("200-1", "결제 대기 주문 수", orderFacade.countPaymentPendingOfBuyer(memberId));
    }

    // 회원이 참여한 공동 주문
    @GetMapping("/internal/group-orders/shared")
    public RsData<List<SharedOrderItem>> getSharedOrders(@RequestParam Long memberId) {
        List<SharedOrderItem> items = sharedGroupOrderFacade.getMyList(memberId).stream()
                .map(o -> new SharedOrderItem(o.id(), o.sharedGroupPurchaseId(), o.status().name(), o.myShareAmount(),
                        o.myPaymentStatus().name(), o.paidCount(), o.participantCount(), o.paymentDeadlineAt()))
                .toList();
        return new RsData<>("200-1", "공동 주문 목록", items);
    }

    // 탈퇴 판단용: 확정된 공동 주문 id (회원 서버가 그 배송이 끝났는지 배송에 다시 묻는다)
    @GetMapping("/internal/group-orders/shared/confirmed-ids")
    public RsData<List<Long>> findConfirmedIds(@RequestParam Long memberId) {
        return new RsData<>("200-1", "확정된 공동 주문", sharedGroupOrderFacade.findConfirmedIdsOfParticipant(memberId));
    }

    // 탈퇴 판단용: 분담금 결제 중인 공동 주문 수
    @GetMapping("/internal/group-orders/shared/paying-count")
    public RsData<Long> countPaying(@RequestParam Long memberId) {
        long count = sharedGroupOrderFacade.findPayingIdsOfParticipant(memberId).size();
        return new RsData<>("200-1", "결제 중인 공동 주문 수", count);
    }

    public record OrderItem(Long id, String orderNumber, String orderType, String status, long totalAmount,
                            int itemCount, String firstProductName, LocalDateTime createdAt) {
    }

    public record SharedOrderItem(Long id, Long sharedGroupPurchaseId, String status, long myShareAmount,
                                  String myPaymentStatus, int paidCount, int participantCount,
                                  LocalDateTime paymentDeadlineAt) {
    }
}
