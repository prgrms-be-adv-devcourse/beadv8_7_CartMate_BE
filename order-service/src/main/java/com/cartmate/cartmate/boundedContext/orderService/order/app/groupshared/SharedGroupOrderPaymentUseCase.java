package com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared;

import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;

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
        if (order.getStatus() == SharedGroupOrderStatus.PAYING) {
            validatePurchasable(order.getItems().stream().map(SharedGroupOrderItem::getInventoryId).toList());
        }
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

    private void validatePurchasable(List<Long> inventoryIds) {
        List<OrderProductInfo> infos = orderProductClient.findAllByInventoryIds(inventoryIds);
        boolean purchasable = infos.size() == inventoryIds.stream().distinct().count()
                && infos.stream().allMatch(OrderProductInfo::purchasable);
        if (!purchasable) {
            throw new ServiceException(OrderErrorCode.ORDER_PRODUCT_NOT_PURCHASABLE);
        }
    }

    PaymentResult pay(Long sharedGroupOrderId, Long memberId, TeamDeliveryAddress teamDeliveryAddress, LocalDateTime now) {
        var existing = orderPaymentClient.findPayment(PaymentOrderType.SHARED_GROUP, sharedGroupOrderId, memberId);
        if (existing.isPresent() && existing.get().isPaid()) {
            return applyOrCompensate(sharedGroupOrderId, memberId, existing.get(), teamDeliveryAddress);
        }
        SharedGroupOrderPaymentInfo info = getPaymentInfo(sharedGroupOrderId, memberId, now);
        if (!info.payable()) {
            throw new ServiceException(OrderErrorCode.ORDER_NOT_PAYABLE);
        }
        if (info.teamAddressRequired() && teamDeliveryAddress == null) {
            throw new ServiceException(OrderErrorCode.ORDER_TEAM_ADDRESS_REQUIRED);
        }
        SharedGroupOrder order = sharedGroupOrderRepository.findById(sharedGroupOrderId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_FOUND));
        validatePurchasable(order.getItems().stream().map(SharedGroupOrderItem::getInventoryId).toList());
        if (info.needsStockReservation()) {
            markStockReserved(sharedGroupOrderId);
        }
        PaymentResult payment = orderPaymentClient.charge(
                PaymentOrderType.SHARED_GROUP, sharedGroupOrderId, memberId, info.shareAmount());
        return applyOrCompensate(sharedGroupOrderId, memberId, payment, teamDeliveryAddress);
    }

    private PaymentResult applyOrCompensate(Long sharedGroupOrderId, Long memberId, PaymentResult payment, TeamDeliveryAddress teamDeliveryAddress) {
        try {
            applyPayment(sharedGroupOrderId, memberId, payment.paymentId(), payment.paidAt(), teamDeliveryAddress);
            return payment;
        } catch (InternalServerUnavailableException e) {
            throw e;
        } catch (ServiceException e) {
            if (!isMissingTeamAddress(e)) {
                orderPaymentClient.compensateCharge(PaymentOrderType.SHARED_GROUP, sharedGroupOrderId, memberId);
            }
            throw e;
        }
    }

    private static boolean isMissingTeamAddress(ServiceException e) {
        return OrderErrorCode.ORDER_TEAM_ADDRESS_REQUIRED.resultCode().equals(e.getResultCode());
    }
}
