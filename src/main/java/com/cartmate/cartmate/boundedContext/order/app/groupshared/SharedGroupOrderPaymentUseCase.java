package com.cartmate.cartmate.boundedContext.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderParticipant;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.dto.SharedGroupOrderPaymentInfo;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class SharedGroupOrderPaymentUseCase {

    private final SharedGroupOrderRepository sharedGroupOrderRepository;
    private final SharedGroupOrderSupport sharedGroupOrderSupport;
    private final ApplicationEventPublisher eventPublisher;

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
        DeliveryAddress deliveryAddress = order.isCreator(memberId) ? toDeliveryAddress(teamDeliveryAddress) : null;
        if (order.pay(memberId, paymentId, paidAt, deliveryAddress)) {
            eventPublisher.publishEvent(toConfirmedEvent(order));
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
                                item.getQuantity(),
                                item.getUnitPrice()))
                        .toList()
        );
    }
}
