package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.order.dto.personal.IndividualGroupOrderCreateRequest;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderCreateRequest;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderCreateResponse;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderDetailResponse;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderSummaryResponse;
import com.cartmate.cartmate.shared.order.dto.OrderPaymentInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderCreateUseCase orderCreateUseCase;
    private final OrderQueryUseCase orderQueryUseCase;
    private final OrderPaymentUseCase orderPaymentUseCase;
    private final OrderCancelUseCase orderCancelUseCase;

    @Transactional
    public OrderCreateResponse createPersonal(Long memberId, OrderCreateRequest request) {
        return orderCreateUseCase.createPersonal(memberId, request.cartItemIds(),
                request.deliveryAddress().toDeliveryAddress(), LocalDateTime.now());
    }

    @Transactional
    public OrderCreateResponse createIndividualGroup(Long memberId, IndividualGroupOrderCreateRequest request) {
        return orderCreateUseCase.createIndividualGroup(memberId, request.individualGroupPurchaseId(), request.items(),
                request.deliveryAddress().toDeliveryAddress(), LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getMyList(Long memberId) {
        return orderQueryUseCase.getMyList(memberId);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getDetail(Long memberId, Long orderId) {
        return orderQueryUseCase.getDetail(memberId, orderId);
    }

    @Transactional(readOnly = true)
    public OrderPaymentInfo getPaymentInfo(Long orderId, Long memberId, LocalDateTime now) {
        return orderPaymentUseCase.getPaymentInfo(orderId, memberId, now);
    }

    @Transactional
    public void applyPayment(Long orderId, Long memberId, LocalDateTime paidAt) {
        orderPaymentUseCase.applyPayment(orderId, memberId, paidAt);
    }

    @Transactional
    public void cancelByBuyer(Long memberId, Long orderId) {
        orderCancelUseCase.cancelByBuyer(memberId, orderId);
    }

    @Transactional
    public void cancelByDeadline(Long orderId) {
        orderCancelUseCase.cancelByDeadline(orderId);
    }

    @Transactional
    public void cancelByOutOfStock(Long orderId) {
        orderCancelUseCase.cancelByOutOfStock(orderId);
    }

    @Transactional(readOnly = true)
    public List<Long> findIdsToCancelByDeadline(LocalDateTime now) {
        return orderCancelUseCase.findIdsToCancelByDeadline(now);
    }
}
