package com.cartmate.cartmate.boundedContext.payment.app;

import com.cartmate.cartmate.boundedContext.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentRefund;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentRefundReason;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentStatus;
import com.cartmate.cartmate.boundedContext.payment.domain.PaymentType;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentRefundRepository;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentRepository;
import com.cartmate.cartmate.boundedContext.payment.out.PaymentWalletClient;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentRefundUseCaseTest {

    private static final long ORDER_ID = 100L;
    private static final long MEMBER_ID = 1L;
    private static final long WALLET_ID = 7L;
    private static final long PAYMENT_ID = 5L;
    private static final long REFUND_ID = 9L;

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentRefundRepository paymentRefundRepository = mock(PaymentRefundRepository.class);
    private final PaymentWalletClient paymentWalletClient = mock(PaymentWalletClient.class);
    private PaymentRefundUseCase useCase;
    private Payment payment;

    @BeforeEach
    void setUp() {
        useCase = new PaymentRefundUseCase(paymentRepository, paymentRefundRepository, paymentWalletClient);

        payment = Payment.pay(ORDER_ID, MEMBER_ID, WALLET_ID, 30000L, PaymentType.INDIVIDUAL_GROUP);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        when(paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.INDIVIDUAL_GROUP, ORDER_ID, MEMBER_ID))
                .thenReturn(Optional.of(payment));
        when(paymentRefundRepository.saveAndFlush(any(PaymentRefund.class))).thenAnswer(invocation -> {
            PaymentRefund saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", REFUND_ID);
            return saved;
        });
    }

    @Test
    @DisplayName("구매자 취소 시 결제 금액을 전액 환불하고 결제는 취소 상태가 된다")
    void refundByBuyerCancel() {
        useCase.refundByBuyerCancel(ORDER_ID, MEMBER_ID);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getRefundedAmount()).isEqualTo(30000L);

        ArgumentCaptor<PaymentRefund> captor = ArgumentCaptor.forClass(PaymentRefund.class);
        verify(paymentRefundRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRefundKey()).isEqualTo("ORDER-CANCEL-" + PAYMENT_ID);
        assertThat(captor.getValue().getAmount()).isEqualTo(30000L);
        assertThat(captor.getValue().getReason()).isEqualTo(PaymentRefundReason.BUYER_CANCEL);
        verify(paymentWalletClient).refund(WALLET_ID, 30000L, PAYMENT_ID, ORDER_ID, REFUND_ID);
    }

    @Test
    @DisplayName("같은 취소로 이미 환불했으면 다시 환불하지 않는다")
    void refundByBuyerCancelIsIdempotent() {
        when(paymentRefundRepository.existsByRefundKey("ORDER-CANCEL-" + PAYMENT_ID)).thenReturn(true);

        useCase.refundByBuyerCancel(ORDER_ID, MEMBER_ID);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRefundRepository, never()).saveAndFlush(any());
        verify(paymentWalletClient, never()).refund(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("환불할 결제가 없으면 결제 없음 오류가 난다")
    void refundByBuyerCancelWithoutPayment() {
        when(paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.INDIVIDUAL_GROUP, 999L, MEMBER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.refundByBuyerCancel(999L, MEMBER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PAYMENT_NOT_FOUND");
        verify(paymentWalletClient, never()).refund(any(), any(), any(), any(), any());
    }
}
