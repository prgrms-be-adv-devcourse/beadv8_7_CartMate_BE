package com.cartmate.cartmate.boundedContext.payout.app;

import com.cartmate.cartmate.boundedContext.payout.domain.Payout;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayoutCreateUseCase {

    private final PayoutRepository payoutRepository;

    // 구매확정된 배송의 상품별로 대기 상태의 정산을 만든다. 이미 정산이 있는 주문상품은 건너뛰어 같은 이벤트가 다시 와도 안전하다.
    public void create(DeliveryConfirmedEvent event) {
        List<Long> orderItemIds = event.items().stream()
                .map(DeliveryConfirmedEvent.Item::orderItemId)
                .toList();

        Set<Long> existing = payoutRepository.findAllByOrderItemIdIn(orderItemIds).stream()
                .map(Payout::getOrderItemId)
                .collect(Collectors.toSet());

        List<Payout> payouts = event.items().stream()
                .filter(item -> !existing.contains(item.orderItemId()))
                .map(item -> Payout.create(
                        event.sellerId(), event.orderId(), item.orderItemId(),
                        item.unitPrice() * item.quantity(), event.confirmedAt()))
                .toList();

        payoutRepository.saveAll(payouts);
    }
}
