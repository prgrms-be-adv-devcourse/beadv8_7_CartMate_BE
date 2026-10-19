package com.cartmate.cartmate.boundedContext.payment.app;

import com.cartmate.cartmate.boundedContext.payment.dto.PaymentPreviewResponse;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import com.cartmate.cartmate.boundedContext.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentOrderClient;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentOrderInfo;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentRepository;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentWalletClient;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentFacade {
    private final PaymentPayUseCase paymentPayUseCase;
    private final PaymentCancelUseCase paymentCancelUseCase;
    private final PaymentRepository paymentRepository;
    private final PaymentOrderClient paymentOrderClient;
    private final PaymentWalletClient paymentWalletClient;

    @Transactional
    public PaymentResponse payOrder(Long memberId, Long orderId) {
        return paymentPayUseCase.payOrder(memberId, orderId);
    }

    @Transactional
    public PaymentResponse cancel(Long memberId, Long paymentId) {
        return paymentCancelUseCase.cancel(memberId, paymentId);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByOrder(Long memberId, Long orderId) {
        return paymentRepository.findByOrderIdAndMemberId(orderId, memberId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PaymentPreviewResponse preview(Long memberId, Long orderId) {
        PaymentOrderInfo order = paymentOrderClient.findOrder(orderId)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_ORDER_NOT_FOUND));
        if (!order.memberId().equals(memberId)) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_ORDER_ACCESS_DENIED);
        }

        PaymentResponse existing = paymentRepository.findByOrderIdAndMemberId(orderId, memberId)
                .map(PaymentResponse::from)
                .orElse(null);

        long balance = paymentWalletClient.getBalance(memberId);
        return new PaymentPreviewResponse(
                orderId,
                order.amount(),
                order.payable(),
                balance,
                balance - order.amount(),
                balance >= order.amount(),
                existing
        );
    }
}