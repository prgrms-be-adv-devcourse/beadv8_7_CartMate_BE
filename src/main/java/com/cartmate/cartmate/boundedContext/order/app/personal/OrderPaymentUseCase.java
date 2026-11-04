package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderItem;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.order.out.personal.OrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.dto.DeliveryAddressInfo;
import com.cartmate.cartmate.shared.order.dto.OrderPaymentInfo;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
class OrderPaymentUseCase {

    private final OrderRepository orderRepository;
    private final OrderSupport orderSupport;
    private final DomainEventPublisher eventPublisher;
    private final OrderProductClient orderProductClient;
    private final IndividualGroupPurchaseReplicaUseCase individualGroupPurchaseReplicaUseCase;

    OrderPaymentInfo getPaymentInfo(Long orderId, Long memberId, LocalDateTime now) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_FOUND));
        orderSupport.validateBuyer(order, memberId);
        return new OrderPaymentInfo(
                order.getId(),
                memberId,
                order.getOrderNumber(),
                order.getTotalAmount(),
                order.canAcceptPayment(now) && soldByGroupPurchaseAtOrder(order),
                order.getPaymentDeadlineAt()
        );
    }

    void applyPayment(Long orderId, Long memberId, Long paymentId, LocalDateTime paidAt) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        orderSupport.validateBuyer(order, memberId);
        validatePurchasable(order.getItems().stream().map(OrderItem::getInventoryId).toList());
        // 같은 결제의 재통보(이미 PAID)는 markPaid가 멱등으로 넘기므로 결제 대기일 때만 다시 본다
        if (order.isPaymentPending() && !soldByGroupPurchaseAtOrder(order)) {
            throw new ServiceException(OrderErrorCode.ORDER_GROUP_PURCHASE_NOT_SOLD_AT_ORDER);
        }
        if (order.markPaid(paymentId, paidAt)) {
            eventPublisher.publish(toPaidEvent(order));
        }
    }

    private OrderPaidEvent toPaidEvent(Order order) {
        DeliveryAddress address = order.getDeliveryAddress();
        return new OrderPaidEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getBuyerMemberId(),
                order.getOrderType().name(),
                order.getIndividualGroupPurchaseId(),
                order.getPaidAt(),
                new DeliveryAddressInfo(
                        address.getReceiverName(),
                        address.getReceiverPhone(),
                        address.getZipCode(),
                        address.getAddress(),
                        address.getAddressDetail()
                ),
                order.getItems().stream()
                        .map(item -> new OrderPaidEvent.Item(
                                item.getId(),
                                item.getProductId(),
                                item.getInventoryId(),
                                item.getSellerId(),
                                item.getProductName(),
                                item.getOptionName(),
                                item.getQuantity(),
                                item.getUnitPrice()))
                        .toList()
        );
    }

    // 개인물품 주문은 cart 공동구매를 잠그지 않고 order 사본으로 만들어진다. 사본이 늦었던 1~2초 사이에
    // 종료 · 마감 · 상품 제거 뒤의 주문이 생길 수 있어, 결제 전에 "주문 순간 팔고 있었나"를 다시 본다(ARCH-16).
    // 결제까지는 보통 수 초 이상 걸려 그사이 사본이 따라잡는다. 사본이 없으면 cart에 잠그지 않고 묻는다
    private boolean soldByGroupPurchaseAtOrder(Order order) {
        if (!order.isIndividualGroup()) {
            return true;
        }
        return individualGroupPurchaseReplicaUseCase.find(order.getIndividualGroupPurchaseId())
                .map(purchase -> purchase.wasSellingAt(order.productIds(), order.getCreatedAt()))
                .orElse(false);
    }

    // 판매글 판매 중지 등으로 주문 상품 하나라도 판매 중이 아니면 결제 전체를 거절한다 (지갑 차감까지 롤백).
    // 결제 정보 조회와 결제 반영 사이에 판매글이 내려가는 경우를 주문을 잠근 상태에서 막는다.
    private void validatePurchasable(List<Long> inventoryIds) {
        List<OrderProductInfo> infos = orderProductClient.findAllByInventoryIds(inventoryIds);
        boolean purchasable = infos.size() == inventoryIds.stream().distinct().count()
                && infos.stream().allMatch(OrderProductInfo::purchasable);
        if (!purchasable) {
            throw new ServiceException(OrderErrorCode.ORDER_PRODUCT_NOT_PURCHASABLE);
        }
    }
}
