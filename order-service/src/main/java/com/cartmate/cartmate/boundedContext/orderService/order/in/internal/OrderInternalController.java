package com.cartmate.cartmate.boundedContext.orderService.order.in.internal;

import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.order.dto.SharedGroupOrderActivity;
import com.cartmate.cartmate.shared.order.dto.OrderActivity;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Hidden
@RestController
@RequiredArgsConstructor
public class OrderInternalController {

    private final OrderFacade orderFacade;
    private final SharedGroupOrderFacade sharedGroupOrderFacade;

    @GetMapping("/internal/orders")
    public RsData<List<OrderActivity>> getOrders(@RequestParam Long memberId) {
        List<OrderActivity> items = orderFacade.getMyList(memberId).stream()
                .map(o -> new OrderActivity(o.id(), o.orderNumber(), o.orderType(), o.status(),
                        o.totalAmount(), o.itemCount(), o.firstProductName(), o.createdAt()))
                .toList();
        return new RsData<>("200-1", "주문 목록", items);
    }

    @GetMapping("/internal/orders/payment-pending-count")
    public RsData<Long> countPaymentPending(@RequestParam Long memberId) {
        return new RsData<>("200-1", "결제 대기 주문 수", orderFacade.countPaymentPendingOfBuyer(memberId));
    }

    @GetMapping("/internal/group-orders/shared")
    public RsData<List<SharedGroupOrderActivity>> getSharedOrders(@RequestParam Long memberId) {
        List<SharedGroupOrderActivity> items = sharedGroupOrderFacade.getMyList(memberId).stream()
                .map(o -> new SharedGroupOrderActivity(o.id(), o.sharedGroupPurchaseId(), o.status(), o.myShareAmount(),
                        o.myPaymentStatus(), o.paidCount(), o.participantCount(), o.paymentDeadlineAt()))
                .toList();
        return new RsData<>("200-1", "공동 주문 목록", items);
    }

    @GetMapping("/internal/group-orders/shared/confirmed-ids")
    public RsData<List<Long>> findConfirmedIds(@RequestParam Long memberId) {
        return new RsData<>("200-1", "확정된 공동 주문", sharedGroupOrderFacade.findConfirmedIdsOfParticipant(memberId));
    }

    @GetMapping("/internal/group-orders/shared/paying-count")
    public RsData<Long> countPaying(@RequestParam Long memberId) {
        long count = sharedGroupOrderFacade.findPayingIdsOfParticipant(memberId).size();
        return new RsData<>("200-1", "결제 중인 공동 주문 수", count);
    }
}
