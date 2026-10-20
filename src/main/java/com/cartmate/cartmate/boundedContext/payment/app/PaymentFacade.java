package com.cartmate.cartmate.boundedContext.payment.app;

import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentPreviewResponse;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import com.cartmate.cartmate.boundedContext.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentOrderClient;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentRepository;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentWalletClient;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.dto.OrderPaymentInfo;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// payment 모듈의 정문
@Service
@RequiredArgsConstructor
public class PaymentFacade {
    private final PaymentPayUseCase paymentPayUseCase;
    private final PaymentSharedGroupPayUseCase paymentSharedGroupPayUseCase;
    private final PaymentRepository paymentRepository;
    private final PaymentOrderClient paymentOrderClient;
    private final PaymentWalletClient paymentWalletClient;

    // ===== 개인 주문 =====
    @Transactional
    public PaymentResponse payOrder(Long memberId, Long orderId) {
        return paymentPayUseCase.payOrder(memberId, orderId);
    }

    @Transactional(readOnly = true)
    public PaymentPreviewResponse preview(Long memberId, Long orderId) {
        // 주문이 없거나 구매자가 아니면 order가 예외
        OrderPaymentInfo order = paymentOrderClient.getPaymentInfo(orderId, memberId, LocalDateTime.now());

        PaymentResponse existing = paymentRepository
                .findByTypeAndOrderIdAndMemberId(PaymentType.INDIVIDUAL, orderId, memberId)
                .map(PaymentResponse::from)
                .orElse(null);

        long balance = paymentWalletClient.getBalance(memberId);
        return new PaymentPreviewResponse(orderId, order.amount(), order.payable(), balance,
                balance - order.amount(), balance >= order.amount(), existing);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByOrder(Long memberId, Long orderId) {
        return paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.INDIVIDUAL, orderId, memberId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    // ===== 공동물품 구매 =====
    @Transactional
    public PaymentResponse paySharedGroupOrder(Long memberId, Long sharedGroupOrderId, TeamDeliveryAddress address) {
        return paymentSharedGroupPayUseCase.pay(memberId, sharedGroupOrderId, address);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getBySharedGroupOrder(Long memberId, Long sharedGroupOrderId) {
        return paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.GROUP_SHARE, sharedGroupOrderId, memberId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }
}