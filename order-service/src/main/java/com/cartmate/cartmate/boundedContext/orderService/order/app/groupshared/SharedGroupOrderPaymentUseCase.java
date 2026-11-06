package com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrderItem;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrderParticipant;
import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderPaymentClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.orderService.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.dto.SharedGroupOrderPaymentInfo;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderConfirmedEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
class SharedGroupOrderPaymentUseCase {

    private final SharedGroupOrderRepository sharedGroupOrderRepository;
    private final SharedGroupOrderSupport sharedGroupOrderSupport;
    private final DomainEventPublisher eventPublisher;
    private final OrderProductClient orderProductClient;
    private final OrderPaymentClient orderPaymentClient;

    SharedGroupOrderPaymentInfo getPaymentInfo(Long sharedGroupOrderId, Long memberId, LocalDateTime now) {
        SharedGroupOrder order = sharedGroupOrderRepository.findById(sharedGroupOrderId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_FOUND));
        SharedGroupOrderParticipant participant = order.getParticipant(memberId);
        return new SharedGroupOrderPaymentInfo(
                order.getId(),
                memberId,
                participant.getShareAmount(),
                order.canAcceptPayment(now) && !participant.isPaid(),
                order.needsStockReservation(),
                order.isCreator(memberId),
                order.getPaymentDeadlineAt()
        );
    }

    void markStockReserved(Long sharedGroupOrderId) {
        SharedGroupOrder order = sharedGroupOrderSupport.getOrderForUpdate(sharedGroupOrderId);
        if (order.getStatus() != SharedGroupOrderStatus.PAYING) {
            throw new ServiceException(OrderErrorCode.ORDER_NOT_PAYING);
        }
        if (order.isStockReserved()) {
            return;
        }
        order.markStockReserved();
    }

    void applyPayment(Long sharedGroupOrderId, Long memberId, Long paymentId, LocalDateTime paidAt,
                      TeamDeliveryAddress teamDeliveryAddress) {
        SharedGroupOrder order = sharedGroupOrderSupport.getOrderForUpdate(sharedGroupOrderId);
        validatePurchasable(order.getItems().stream().map(SharedGroupOrderItem::getInventoryId).toList());
        DeliveryAddress deliveryAddress = order.isCreator(memberId) ? toDeliveryAddress(teamDeliveryAddress) : null;
        if (order.pay(memberId, paymentId, paidAt, deliveryAddress)) {
            eventPublisher.publish(toConfirmedEvent(order));
        }
    }

    private DeliveryAddress toDeliveryAddress(TeamDeliveryAddress teamDeliveryAddress) {
        if (teamDeliveryAddress == null) {
            return null;
        }
        return DeliveryAddress.of(
                teamDeliveryAddress.receiverName(),
                teamDeliveryAddress.receiverPhone(),
                teamDeliveryAddress.zipCode(),
                teamDeliveryAddress.address(),
                teamDeliveryAddress.addressDetail()
        );
    }

    private SharedGroupOrderConfirmedEvent toConfirmedEvent(SharedGroupOrder order) {
        DeliveryAddress address = order.getTeamDeliveryAddress();
        return new SharedGroupOrderConfirmedEvent(
                order.getId(),
                order.getSharedGroupPurchaseId(),
                order.getCreatorMemberId(),
                order.getConfirmedAt(),
                new TeamDeliveryAddress(
                        address.getReceiverName(),
                        address.getReceiverPhone(),
                        address.getZipCode(),
                        address.getAddress(),
                        address.getAddressDetail()
                ),
                order.getItems().stream()
                        .map(item -> new SharedGroupOrderConfirmedEvent.Item(
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

    // ===== 분담금 결제 (주문이 지휘) =====
    // ① 이미 냈는지 → ② 결제 가능 확인 → ③ 첫 결제면 재고 선점 표시 → ④ 결제에 차감 요청 → ⑤ 잠그고 반영
    // 마지막 참여자 결제면 ⑤에서 주문이 확정되고 확정 이벤트가 나간다 (기존 applyPayment 그대로).
    PaymentResult pay(Long sharedGroupOrderId, Long memberId, TeamDeliveryAddress teamDeliveryAddress, LocalDateTime now) {
        var existing = orderPaymentClient.findPayment(PaymentOrderType.SHARED_GROUP, sharedGroupOrderId, memberId);
        if (existing.isPresent() && existing.get().isPaid()) {
            // 이미 빠졌다 → 반영만 다시 시도 (이미 반영됐으면 아무것도 안 함)
            return applyOrCompensate(sharedGroupOrderId, memberId, existing.get(), teamDeliveryAddress);
        }
        SharedGroupOrderPaymentInfo info = getPaymentInfo(sharedGroupOrderId, memberId, now); // 참여자가 아니면 예외
        if (!info.payable()) {
            throw new ServiceException(OrderErrorCode.ORDER_NOT_PAYABLE);
        }
        // 개설자 팀 배송지는 차감 전에 확인 (빠진 뒤 거절되면 되돌려야 하므로 미리 거른다)
        if (info.teamAddressRequired() && teamDeliveryAddress == null) {
            throw new ServiceException(OrderErrorCode.ORDER_TEAM_ADDRESS_REQUIRED);
        }
        SharedGroupOrder order = sharedGroupOrderRepository.findById(sharedGroupOrderId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_FOUND));
        validatePurchasable(order.getItems().stream().map(SharedGroupOrderItem::getInventoryId).toList());
        // 전에는 결제가 주문에 부탁하던 일. 이제 주문이 스스로 한다.
        // TODO(product): 상품 모듈 재고 선점 API가 나오면 여기서 먼저 재고를 확보한 뒤 표시
        if (info.needsStockReservation()) {
            markStockReserved(sharedGroupOrderId);
        }
        PaymentResult payment = orderPaymentClient.charge(
                PaymentOrderType.SHARED_GROUP, sharedGroupOrderId, memberId, info.shareAmount());
        return applyOrCompensate(sharedGroupOrderId, memberId, payment, teamDeliveryAddress);
    }

    // 반영이 거절되면(그 사이 기한 만료 취소 등) 빠진 분담금을 되돌린다. 일시적 오류는 되돌리지 않고 재시도에 맡긴다.
    private PaymentResult applyOrCompensate(Long sharedGroupOrderId, Long memberId, PaymentResult payment, TeamDeliveryAddress teamDeliveryAddress) {
        try {
            applyPayment(sharedGroupOrderId, memberId, payment.paymentId(), payment.paidAt(), teamDeliveryAddress);
            return payment;
        } catch (ServiceException e) {
            orderPaymentClient.compensateCharge(PaymentOrderType.SHARED_GROUP, sharedGroupOrderId, memberId);
            throw e;
        }
    }
}
