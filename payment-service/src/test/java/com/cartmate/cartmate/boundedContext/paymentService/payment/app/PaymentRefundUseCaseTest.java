package com.cartmate.cartmate.boundedContext.paymentService.payment.app;

import com.cartmate.cartmate.boundedContext.paymentService.payment.domain.Payment;
import com.cartmate.cartmate.boundedContext.paymentService.payment.domain.PaymentRefund;
import com.cartmate.cartmate.boundedContext.paymentService.payment.domain.PaymentRefundReason;
import com.cartmate.cartmate.boundedContext.paymentService.payment.domain.PaymentStatus;
import com.cartmate.cartmate.boundedContext.paymentService.payment.domain.PaymentType;
import com.cartmate.cartmate.boundedContext.paymentService.payment.domain.RefundKey;
import com.cartmate.cartmate.boundedContext.paymentService.payment.out.PaymentRefundRepository;
import com.cartmate.cartmate.boundedContext.paymentService.payment.out.PaymentRepository;
import com.cartmate.cartmate.boundedContext.paymentService.payment.out.PaymentWalletClient;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentRefundUseCaseTest {

    private static final Long SHARED_ORDER_ID = 500L;
    private static final Long SELLER_A = 21L;

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentRefundRepository paymentRefundRepository = mock(PaymentRefundRepository.class);
    private final PaymentWalletClient paymentWalletClient = mock(PaymentWalletClient.class);
    private final PaymentRefundUseCase useCase =
            new PaymentRefundUseCase(paymentRepository, paymentRefundRepository, paymentWalletClient);

    // 저장된 환불 기록 (DB 대신)
    private final List<PaymentRefund> refunds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(paymentRefundRepository.existsByRefundKey(anyString()))
                .thenAnswer(inv -> refunds.stream().anyMatch(r -> r.getRefundKey().equals(inv.getArgument(0))));
        when(paymentRefundRepository.saveAndFlush(any(PaymentRefund.class))).thenAnswer(inv -> {
            PaymentRefund refund = inv.getArgument(0);
            ReflectionTestUtils.setField(refund, "id", (long) refunds.size() + 1);
            refunds.add(refund);
            return refund;
        });
    }

    private static Payment payment(long id, long amount, PaymentType type) {
        Payment payment = Payment.pay(SHARED_ORDER_ID, id, id, amount, type);
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }

    // 참여자 3명. 만든 사람(3번)이 분담금 끝전 1원을 더 냈다.
    private List<Payment> sharedPayments() {
        List<Payment> payments = List.of(
                payment(1L, 10_000, PaymentType.SHARED_GROUP),
                payment(2L, 10_000, PaymentType.SHARED_GROUP),
                payment(3L, 10_001, PaymentType.SHARED_GROUP));
        when(paymentRepository.findAllByTypeAndOrderId(PaymentType.SHARED_GROUP, SHARED_ORDER_ID)).thenReturn(payments);
        return payments;
    }

    private long refundedTo(Payment payment) {
        return refunds.stream().filter(r -> r.getPaymentId().equals(payment.getId()))
                .mapToLong(PaymentRefund::getAmount).sum();
    }

    private void assertFullyRefunded(List<Payment> payments) {
        for (Payment payment : payments) {
            assertThat(refundedTo(payment)).isEqualTo(payment.getAmount());
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        }
    }

    @Test
    @DisplayName("공동 주문: 판매자 항목 취소 환불이 먼저 오고 전체 취소 환불이 뒤에 와도 사람마다 낸 금액만큼 환불된다")
    void sellerItemsFirstThenGroupCancel() {
        List<Payment> payments = sharedPayments();

        useCase.refundSharedSellerItems(SHARED_ORDER_ID, SELLER_A, 10_001, DeliveryCancelReason.SELLER_OUT_OF_STOCK);
        useCase.refundCanceledSharedGroupOrder(SHARED_ORDER_ID, "SALES_STOPPED", List.of(10_001L));

        assertFullyRefunded(payments);
        assertThat(refundedTo(payments.get(2))).isEqualTo(10_001);
    }

    @Test
    @DisplayName("공동 주문: 전체 취소 환불이 먼저 오고 판매자 항목 취소 환불이 뒤에 와도 사람마다 낸 금액만큼 환불된다")
    void groupCancelFirstThenSellerItems() {
        List<Payment> payments = sharedPayments();

        useCase.refundCanceledSharedGroupOrder(SHARED_ORDER_ID, "SALES_STOPPED", List.of(10_001L));
        // 전체 취소는 참여자 몫(3,334 / 3,334 / 3,333)을 남기고 환불한다
        assertThat(payments).extracting(Payment::refundableAmount).containsExactly(3_334L, 3_333L, 3_334L);

        useCase.refundSharedSellerItems(SHARED_ORDER_ID, SELLER_A, 10_001, DeliveryCancelReason.SELLER_OUT_OF_STOCK);

        assertFullyRefunded(payments);
    }

    @Test
    @DisplayName("공동 주문: 먼저 취소된 판매자 항목이 여럿이어도 어느 순서로 와도 사람마다 낸 금액만큼 환불된다")
    void severalSellerItemsAroundGroupCancel() {
        List<Payment> payments = sharedPayments();

        useCase.refundSharedSellerItems(SHARED_ORDER_ID, SELLER_A, 10_001, DeliveryCancelReason.SELLER_OTHER);
        useCase.refundCanceledSharedGroupOrder(SHARED_ORDER_ID, "SALES_STOPPED", List.of(10_001L, 5_000L));
        useCase.refundSharedSellerItems(SHARED_ORDER_ID, 22L, 5_000, null);

        assertFullyRefunded(payments);
    }

    @Test
    @DisplayName("공동 주문: 먼저 취소된 판매자 항목 목록이 없으면 사람마다 낸 금액 전부를 환불한다")
    void groupCancelWithoutList() {
        List<Payment> payments = sharedPayments();

        int refunded = useCase.refundCanceledSharedGroupOrder(SHARED_ORDER_ID, "SALES_STOPPED", null);

        assertThat(refunded).isEqualTo(3);
        assertFullyRefunded(payments);
    }

    @Test
    @DisplayName("공동 주문: 주문 반영 실패로 되돌린 결제는 나누는 대상에서 빠진다")
    void excludesCompensatedPayment() {
        Payment compensated = payment(4L, 10_000, PaymentType.SHARED_GROUP);
        List<Payment> payments = new ArrayList<>(sharedPayments());
        payments.add(compensated);
        when(paymentRepository.findAllByTypeAndOrderId(PaymentType.SHARED_GROUP, SHARED_ORDER_ID)).thenReturn(payments);
        when(paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.SHARED_GROUP, SHARED_ORDER_ID, 4L))
                .thenReturn(Optional.of(compensated));
        useCase.compensateCharge(PaymentType.SHARED_GROUP, SHARED_ORDER_ID, 4L);

        useCase.refundSharedSellerItems(SHARED_ORDER_ID, SELLER_A, 10_001, DeliveryCancelReason.SALES_STOPPED);

        assertThat(payments.subList(0, 3)).extracting(Payment::refundableAmount)
                .containsExactly(6_666L, 6_667L, 6_667L);
    }

    @Test
    @DisplayName("판매자 사유 취소는 판매 중지와 다른 환불 사유·메모로 남는다")
    void sellerReasonHasOwnRefundReason() {
        Payment payment = payment(1L, 30_000, PaymentType.INDIVIDUAL_GROUP);
        when(paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.INDIVIDUAL_GROUP, 77L, 1L))
                .thenReturn(Optional.of(payment));

        useCase.refundOrderSellerItems(77L, 1L, SELLER_A, 10_000, DeliveryCancelReason.SELLER_OUT_OF_STOCK);

        assertThat(refunds).singleElement().satisfies(r -> {
            assertThat(r.getReason()).isEqualTo(PaymentRefundReason.SELLER_CANCEL);
            assertThat(r.getMemo()).isEqualTo("판매자 사유 취소 자동 환불(품절·재고 부족)");
            assertThat(r.getRefundKey()).isEqualTo(RefundKey.orderSellerItems(77L, SELLER_A));
        });
    }

    @Test
    @DisplayName("사유가 판매 중지이거나 없으면 판매 중지 환불로 남는다")
    void salesStoppedOrNullIsProductHidden() {
        Payment payment = payment(1L, 30_000, PaymentType.INDIVIDUAL_GROUP);
        when(paymentRepository.findByTypeAndOrderIdAndMemberId(eq(PaymentType.INDIVIDUAL_GROUP), any(), eq(1L)))
                .thenReturn(Optional.of(payment));

        useCase.refundOrderSellerItems(77L, 1L, SELLER_A, 10_000, DeliveryCancelReason.SALES_STOPPED);
        useCase.refundOrderSellerItems(78L, 1L, SELLER_A, 10_000, null);

        assertThat(refunds).extracting(PaymentRefund::getReason)
                .containsExactly(PaymentRefundReason.PRODUCT_HIDDEN, PaymentRefundReason.PRODUCT_HIDDEN);
        assertThat(refunds).extracting(PaymentRefund::getMemo).containsOnly("판매 중지 상품 자동 환불");
    }

    @Test
    @DisplayName("같은 판매자 항목이 판매 중지와 판매자 사유로 두 번 와도 한 번만 환불된다")
    void sameSellerItemsRefundedOnce() {
        Payment payment = payment(9L, 30_000, PaymentType.INDIVIDUAL_GROUP);
        when(paymentRepository.findByTypeAndOrderIdAndMemberId(PaymentType.INDIVIDUAL_GROUP, 77L, 9L))
                .thenReturn(Optional.of(payment));
        List<Payment> shared = sharedPayments();

        useCase.refundOrderSellerItems(77L, 9L, SELLER_A, 10_000, DeliveryCancelReason.SALES_STOPPED);
        useCase.refundOrderSellerItems(77L, 9L, SELLER_A, 10_000, DeliveryCancelReason.SELLER_OUT_OF_STOCK);
        useCase.refundSharedSellerItems(SHARED_ORDER_ID, SELLER_A, 10_001, DeliveryCancelReason.SALES_STOPPED);
        useCase.refundSharedSellerItems(SHARED_ORDER_ID, SELLER_A, 10_001, DeliveryCancelReason.SELLER_OUT_OF_STOCK);

        assertThat(payment.refundableAmount()).isEqualTo(20_000);
        assertThat(shared.stream().mapToLong(this::refundedTo).sum()).isEqualTo(10_001);
        assertThat(refunds).hasSize(4);
    }
}
