package com.cartmate.cartmate.boundedContext.payout.app;

import com.cartmate.cartmate.boundedContext.payout.domain.Payout;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutPurchaseType;
import com.cartmate.cartmate.boundedContext.payout.exception.PayoutErrorCode;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
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

    // 구매확정된 배송의 상품별로 대기 상태의 정산을 만든다. 같은 구매 유형에서 이미 정산이 있는 주문상품은 건너뛰어 같은 이벤트가 다시 와도 안전하다.
    public void create(DeliveryConfirmedEvent event) {
        if (event.purchaseType() == null) {
            throw new ServiceException(PayoutErrorCode.PAYOUT_REQUIRED_VALUE_MISSING);
        }
        PayoutPurchaseType purchaseType = PayoutPurchaseType.valueOf(event.purchaseType().name());
        List<Long> orderItemIds = event.items().stream()
                .map(DeliveryConfirmedEvent.Item::orderItemId)
                .toList();

        Set<Long> existing = payoutRepository.findAllByPurchaseTypeAndOrderItemIdIn(purchaseType, orderItemIds).stream()
                .map(Payout::getOrderItemId)
                .collect(Collectors.toSet());

        List<Payout> payouts = event.items().stream()
                .filter(item -> !existing.contains(item.orderItemId()))
                .map(item -> Payout.create(
                        event.sellerId(), event.orderId(), purchaseType, item.orderItemId(), item.productName(),
                        item.quantity(), item.unitPrice() * item.quantity(), event.orderedAt(), event.confirmedAt()))
                .toList();

        payoutRepository.saveAll(payouts);
    }
}
