package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletChargeOrderInfo;
import com.cartmate.cartmate.boundedContext.wallet.domain.ChargeStatus;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletChargeOrder;
import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayException;
import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayException.FailureType;
import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayPayment;
import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayPayment.Status;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletChargeGateway;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WalletChargeConfirmServiceTest {

    private static final Long WALLET_ID = 1L;
    private static final String ORDER_NUMBER = "Charge_11111111-2222-3333-4444-555555555555";
    private static final String PAYMENT_KEY = "pk_1";
    private static final String IDEMPOTENCY_KEY = "idem-1";
    private static final Long AMOUNT = 10_000L;

    private final WalletFacade walletFacade = mock(WalletFacade.class);
    private final WalletRetryService walletRetryService = mock(WalletRetryService.class);
    private final WalletChargeGateway chargeGateway = mock(WalletChargeGateway.class);
    private WalletChargeConfirmService service;

    @BeforeEach
    void setUp() {
        service = new WalletChargeConfirmService(
                walletFacade, new WalletChargeSettlement(walletFacade, walletRetryService), chargeGateway);
        order(ChargeStatus.PENDING, null);
    }

    private void order(ChargeStatus status, String paymentKey) {
        when(walletFacade.getChargeOrder(ORDER_NUMBER)).thenReturn(new WalletChargeOrderInfo(
                WALLET_ID, ORDER_NUMBER, AMOUNT, status, paymentKey, IDEMPOTENCY_KEY));
    }

    private ChargeGatewayPayment payment(Status status) {
        return new ChargeGatewayPayment(PAYMENT_KEY, ORDER_NUMBER, AMOUNT, status);
    }

    private ChargeGatewayException failure(FailureType type) {
        return new ChargeGatewayException(type, "CODE", "오류");
    }

    private void confirmApproved(ChargeGatewayPayment payment) {
        when(chargeGateway.confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT, IDEMPOTENCY_KEY)).thenReturn(payment);
    }

    private void confirmFails(FailureType type) {
        when(chargeGateway.confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT, IDEMPOTENCY_KEY)).thenThrow(failure(type));
    }

    private void confirm() {
        service.confirm(WALLET_ID, ORDER_NUMBER, PAYMENT_KEY, AMOUNT);
    }

    private void assertError(Runnable action, WalletErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(code.resultCode()));
    }

    private void verifyCharged() {
        verify(walletRetryService).charge(WALLET_ID, ORDER_NUMBER, PAYMENT_KEY, AMOUNT);
    }

    private void verifyNotCharged() {
        verify(walletRetryService, never()).charge(anyLong(), anyString(), anyString(), anyLong());
    }

    private void verifyNotFailed() {
        verify(walletFacade, never()).fail(anyLong(), anyString());
    }

    @Test
    @DisplayName("승인이 완료되면 충전 주문 금액으로 승인을 요청하고 충전을 완료한다")
    void confirmAndCharge() {
        confirmApproved(payment(Status.DONE));

        confirm();

        verify(chargeGateway).confirm(PAYMENT_KEY, ORDER_NUMBER, AMOUNT, IDEMPOTENCY_KEY);
        verifyCharged();
        verifyNotFailed();
    }

    @Test
    @DisplayName("충전 주문이 없으면 예외가 발생하고 결제사를 호출하지 않는다")
    void orderNotFound() {
        when(walletFacade.getChargeOrder(ORDER_NUMBER))
                .thenThrow(new ServiceException(WalletErrorCode.WALLET_CHARGE_ORDER_NOT_FOUND));

        assertError(this::confirm, WalletErrorCode.WALLET_CHARGE_ORDER_NOT_FOUND);
        verifyNoInteractions(chargeGateway, walletRetryService);
    }

    @Test
    @DisplayName("다른 사람의 충전 주문이면 예외가 발생하고 결제사를 호출하지 않는다")
    void notOwner() {
        assertError(() -> service.confirm(2L, ORDER_NUMBER, PAYMENT_KEY, AMOUNT), WalletErrorCode.WALLET_ACCESS_DENIED);
        verifyNoInteractions(chargeGateway, walletRetryService);
    }

    @Test
    @DisplayName("결제 키가 비어 있으면 예외가 발생하고 결제사를 호출하지 않는다")
    void paymentKeyRequired() {
        assertError(() -> service.confirm(WALLET_ID, ORDER_NUMBER, " ", AMOUNT), WalletErrorCode.WALLET_PAYMENT_KEY_REQUIRED);
        assertError(() -> service.confirm(WALLET_ID, ORDER_NUMBER, null, AMOUNT), WalletErrorCode.WALLET_PAYMENT_KEY_REQUIRED);
        verifyNoInteractions(chargeGateway, walletRetryService);
    }

    @Test
    @DisplayName("클라이언트가 보낸 금액이 주문 금액과 다르면 결제사로 보내지 않고 예외가 발생한다")
    void amountMismatch() {
        assertError(() -> service.confirm(WALLET_ID, ORDER_NUMBER, PAYMENT_KEY, AMOUNT + 1),
                WalletErrorCode.WALLET_CHARGE_AMOUNT_MISMATCH);
        verifyNoInteractions(chargeGateway, walletRetryService);
    }

    @Test
    @DisplayName("이미 완료된 주문을 같은 결제 키로 다시 요청하면 결제사를 호출하지 않고 정상 종료한다")
    void alreadyCompletedWithSamePaymentKey() {
        order(ChargeStatus.COMPLETED, PAYMENT_KEY);

        confirm();

        verifyNoInteractions(chargeGateway, walletRetryService);
        verifyNotFailed();
    }

    @Test
    @DisplayName("이미 완료된 주문을 다른 결제 키로 요청하면 예외가 발생한다")
    void alreadyCompletedWithOtherPaymentKey() {
        order(ChargeStatus.COMPLETED, "pk_other");

        assertError(this::confirm, WalletErrorCode.WALLET_PAYMENT_KEY_MISMATCH);
        verifyNoInteractions(chargeGateway, walletRetryService);
    }

    @ParameterizedTest
    @EnumSource(value = ChargeStatus.class, names = {"CANCELED", "FAILED"})
    @DisplayName("취소되거나 실패한 주문은 승인할 수 없다")
    void notCompletable(ChargeStatus status) {
        order(status, null);

        assertError(this::confirm, WalletErrorCode.WALLET_CHARGE_NOT_COMPLETABLE);
        verifyNoInteractions(chargeGateway, walletRetryService);
    }

    @Test
    @DisplayName("승인이 거절로 확정되면 주문을 실패 처리하고 충전하지 않는다")
    void rejected() {
        confirmFails(FailureType.REJECTED);

        assertError(this::confirm, WalletErrorCode.WALLET_CHARGE_PAYMENT_REJECTED);
        verify(walletFacade).fail(WALLET_ID, ORDER_NUMBER);
        verify(chargeGateway, never()).fetch(anyString());
        verifyNotCharged();
    }

    @Test
    @DisplayName("거절로 확정됐을 때 실패 처리가 이미 처리된 주문이어도 거절 오류를 그대로 준다")
    void rejectedWhenFailOrderAlreadyHandled() {
        confirmFails(FailureType.REJECTED);
        doThrow(new ServiceException(WalletErrorCode.WALLET_CHARGE_NOT_FAILABLE))
                .when(walletFacade).fail(WALLET_ID, ORDER_NUMBER);

        assertError(this::confirm, WalletErrorCode.WALLET_CHARGE_PAYMENT_REJECTED);
        verifyNotCharged();
    }

    @Test
    @DisplayName("거절로 확정됐을 때 실패 처리가 락 충돌 같은 예외로 실패해도 거절 오류를 그대로 준다")
    void rejectedWhenFailOrderThrowsUnexpected() {
        confirmFails(FailureType.REJECTED);
        doThrow(new ObjectOptimisticLockingFailureException(WalletChargeOrder.class, 1L))
                .when(walletFacade).fail(WALLET_ID, ORDER_NUMBER);

        assertError(this::confirm, WalletErrorCode.WALLET_CHARGE_PAYMENT_REJECTED);
        verifyNotCharged();
    }

    @Test
    @DisplayName("이미 처리된 결제는 실패로 보지 않고 조회해서 승인 완료면 충전한다")
    void alreadyProcessedThenDone() {
        confirmFails(FailureType.ALREADY_PROCESSED);
        when(chargeGateway.fetch(PAYMENT_KEY)).thenReturn(payment(Status.DONE));

        confirm();

        verifyCharged();
        verifyNotFailed();
    }

    @Test
    @DisplayName("승인 결과를 알 수 없으면 조회해서 승인 완료일 때 충전한다")
    void unknownThenDone() {
        confirmFails(FailureType.UNKNOWN);
        when(chargeGateway.fetch(PAYMENT_KEY)).thenReturn(payment(Status.DONE));

        confirm();

        verifyCharged();
        verifyNotFailed();
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"IN_PROGRESS", "UNKNOWN"})
    @DisplayName("승인 결과를 알 수 없고 조회해도 끝나지 않았으면 주문을 대기 상태로 두고 처리 중 오류를 준다")
    void unknownThenNotFinished(Status status) {
        confirmFails(FailureType.UNKNOWN);
        when(chargeGateway.fetch(PAYMENT_KEY)).thenReturn(payment(status));

        assertError(this::confirm, WalletErrorCode.WALLET_CHARGE_PAYMENT_PROCESSING);
        verifyNotFailed();
        verifyNotCharged();
    }

    @Test
    @DisplayName("승인 결과를 알 수 없고 조회마저 실패하면 주문을 대기 상태로 두고 처리 중 오류를 준다")
    void unknownThenFetchFails() {
        confirmFails(FailureType.UNKNOWN);
        when(chargeGateway.fetch(PAYMENT_KEY)).thenThrow(failure(FailureType.UNKNOWN));

        assertError(this::confirm, WalletErrorCode.WALLET_CHARGE_PAYMENT_PROCESSING);
        verifyNotFailed();
        verifyNotCharged();
    }

    @Test
    @DisplayName("이미 처리된 결제를 조회했더니 거절 쪽 오류가 나도 주문을 실패 처리하지 않는다")
    void alreadyProcessedThenFetchRejected() {
        confirmFails(FailureType.ALREADY_PROCESSED);
        when(chargeGateway.fetch(PAYMENT_KEY)).thenThrow(failure(FailureType.REJECTED));

        assertError(this::confirm, WalletErrorCode.WALLET_CHARGE_PAYMENT_PROCESSING);
        verifyNotFailed();
        verifyNotCharged();
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"CANCELED", "FAILED"})
    @DisplayName("조회했더니 취소되거나 실패한 결제면 주문을 실패 처리하고 충전하지 않는다")
    void unknownThenCanceledOrFailed(Status status) {
        confirmFails(FailureType.UNKNOWN);
        when(chargeGateway.fetch(PAYMENT_KEY)).thenReturn(payment(status));

        assertError(this::confirm, WalletErrorCode.WALLET_CHARGE_PAYMENT_REJECTED);
        verify(walletFacade).fail(WALLET_ID, ORDER_NUMBER);
        verifyNotCharged();
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"CANCELED", "FAILED"})
    @DisplayName("승인 응답이 취소·실패 상태이면 주문을 실패 처리하고 충전하지 않는다")
    void approvedResponseCanceledOrFailed(Status status) {
        confirmApproved(payment(status));

        assertError(this::confirm, WalletErrorCode.WALLET_CHARGE_PAYMENT_REJECTED);
        verify(walletFacade).fail(WALLET_ID, ORDER_NUMBER);
        verifyNotCharged();
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"IN_PROGRESS", "UNKNOWN"})
    @DisplayName("승인 응답이 아직 끝나지 않은 상태이면 처리 중 오류를 주고 주문은 대기 상태로 둔다")
    void approvedResponseNotFinished(Status status) {
        confirmApproved(payment(status));

        assertError(this::confirm, WalletErrorCode.WALLET_CHARGE_PAYMENT_PROCESSING);
        verifyNotFailed();
        verifyNotCharged();
    }

    @Test
    @DisplayName("승인된 금액이 주문 금액과 다르면 충전하지 않고 주문도 실패 처리하지 않는다")
    void approvedAmountMismatch() {
        confirmApproved(new ChargeGatewayPayment(PAYMENT_KEY, ORDER_NUMBER, AMOUNT + 1, Status.DONE));

        assertError(this::confirm, WalletErrorCode.WALLET_CHARGE_PAYMENT_MISMATCH);
        verifyNotFailed();
        verifyNotCharged();
    }

    @Test
    @DisplayName("승인된 주문번호가 충전 주문과 다르면 충전하지 않는다")
    void approvedOrderNumberMismatch() {
        confirmApproved(new ChargeGatewayPayment(PAYMENT_KEY, "Charge_other", AMOUNT, Status.DONE));

        assertError(this::confirm, WalletErrorCode.WALLET_CHARGE_PAYMENT_MISMATCH);
        verifyNotFailed();
        verifyNotCharged();
    }

    @Test
    @DisplayName("승인된 결제 키가 요청한 결제 키와 다르면 충전하지 않는다")
    void approvedPaymentKeyMismatch() {
        confirmApproved(new ChargeGatewayPayment("pk_other", ORDER_NUMBER, AMOUNT, Status.DONE));

        assertError(this::confirm, WalletErrorCode.WALLET_CHARGE_PAYMENT_MISMATCH);
        verifyNotFailed();
        verifyNotCharged();
    }

    @Test
    @DisplayName("결제는 승인됐는데 충전 반영이 실패하면 예외를 그대로 던진다")
    void chargeFailsAfterApproval() {
        confirmApproved(payment(Status.DONE));
        RuntimeException error = new IllegalStateException("db down");
        doThrow(error).when(walletRetryService).charge(WALLET_ID, ORDER_NUMBER, PAYMENT_KEY, AMOUNT);

        assertThatThrownBy(this::confirm).isSameAs(error);
        verifyNotFailed();
    }
}
