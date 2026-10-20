package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletChargeOrderInfo;
import com.cartmate.cartmate.boundedContext.wallet.domain.ChargeStatus;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletChargeWebhookServiceTest {

    private static final Long WALLET_ID = 1L;
    private static final String ORDER_NUMBER = "Charge_11111111-2222-3333-4444-555555555555";
    private static final String PAYMENT_KEY = "pk_1";
    private static final Long AMOUNT = 10_000L;

    private final WalletFacade walletFacade = mock(WalletFacade.class);
    private final WalletRetryService walletRetryService = mock(WalletRetryService.class);
    private final WalletChargeGateway chargeGateway = mock(WalletChargeGateway.class);
    private WalletChargeWebhookService service;

    @BeforeEach
    void setUp() {
        service = new WalletChargeWebhookService(
                walletFacade, chargeGateway, new WalletChargeSettlement(walletFacade, walletRetryService));
        order(ChargeStatus.PENDING, null);
    }

    private void order(ChargeStatus status, String paymentKey) {
        when(walletFacade.getChargeOrder(ORDER_NUMBER)).thenReturn(new WalletChargeOrderInfo(
                WALLET_ID, ORDER_NUMBER, AMOUNT, status, paymentKey, "idem-1"));
    }

    private void fetched(Status status) {
        when(chargeGateway.fetch(PAYMENT_KEY))
                .thenReturn(new ChargeGatewayPayment(PAYMENT_KEY, ORDER_NUMBER, AMOUNT, status));
    }

    private void verifyNotCharged() {
        verify(walletRetryService, never()).charge(anyLong(), anyString(), anyString(), anyLong());
    }

    private void verifyNotFailed() {
        verify(walletFacade, never()).fail(anyLong(), anyString());
    }

    @Test
    @DisplayName("결제가 승인되었으면 대기 중인 충전 주문을 완료한다")
    void done() {
        fetched(Status.DONE);

        service.sync(PAYMENT_KEY);

        verify(walletRetryService).charge(WALLET_ID, ORDER_NUMBER, PAYMENT_KEY, AMOUNT);
        verifyNotFailed();
    }

    @Test
    @DisplayName("결제가 취소·실패되었으면 대기 중인 충전 주문을 실패 처리한다")
    void canceled() {
        fetched(Status.CANCELED);

        service.sync(PAYMENT_KEY);

        verify(walletFacade).fail(WALLET_ID, ORDER_NUMBER);
        verifyNotCharged();
    }

    @Test
    @DisplayName("실패 처리 반영에 실패하면 예외를 던져 웹훅이 다시 오게 한다")
    void failFails() {
        fetched(Status.CANCELED);
        RuntimeException error = new RuntimeException("DB 오류");
        doThrow(error).when(walletFacade).fail(WALLET_ID, ORDER_NUMBER);

        assertThatThrownBy(() -> service.sync(PAYMENT_KEY)).isSameAs(error);
    }

    @Test
    @DisplayName("실패 처리하려는 사이 이미 확정된 주문이면 정상으로 끝낸다")
    void failAlreadyDecided() {
        fetched(Status.CANCELED);
        doThrow(new ServiceException(WalletErrorCode.WALLET_CHARGE_NOT_FAILABLE))
                .when(walletFacade).fail(WALLET_ID, ORDER_NUMBER);

        service.sync(PAYMENT_KEY);
    }

    @Test
    @DisplayName("취소된 결제라도 금액이 주문과 다르면 주문을 실패 처리하지 않는다")
    void canceledAmountMismatch() {
        when(chargeGateway.fetch(PAYMENT_KEY))
                .thenReturn(new ChargeGatewayPayment(PAYMENT_KEY, ORDER_NUMBER, AMOUNT + 1, Status.CANCELED));

        service.sync(PAYMENT_KEY);

        verifyNotFailed();
        verifyNotCharged();
    }

    @Test
    @DisplayName("아직 진행 중이면 아무것도 바꾸지 않는다")
    void inProgress() {
        fetched(Status.IN_PROGRESS);

        service.sync(PAYMENT_KEY);

        verifyNotCharged();
        verifyNotFailed();
    }

    @Test
    @DisplayName("이미 완료된 주문은 다시 처리하지 않는다")
    void alreadyCompleted() {
        order(ChargeStatus.COMPLETED, PAYMENT_KEY);
        fetched(Status.DONE);

        service.sync(PAYMENT_KEY);

        verifyNotCharged();
        verifyNotFailed();
    }

    @Test
    @DisplayName("충전 주문이 아닌 결제는 무시한다")
    void notChargeOrder() {
        fetched(Status.DONE);
        when(walletFacade.getChargeOrder(ORDER_NUMBER))
                .thenThrow(new ServiceException(WalletErrorCode.WALLET_CHARGE_ORDER_NOT_FOUND));

        service.sync(PAYMENT_KEY);

        verifyNotCharged();
        verifyNotFailed();
    }

    @Test
    @DisplayName("승인 금액이 주문과 다르면 충전하지 않고 재전송도 받지 않는다")
    void amountMismatch() {
        when(chargeGateway.fetch(PAYMENT_KEY))
                .thenReturn(new ChargeGatewayPayment(PAYMENT_KEY, ORDER_NUMBER, AMOUNT + 1, Status.DONE));

        service.sync(PAYMENT_KEY);

        verifyNotCharged();
        verifyNotFailed();
    }

    @Test
    @DisplayName("결제사 조회가 실패하면 예외를 던져 웹훅이 다시 오게 한다")
    void fetchFails() {
        ChargeGatewayException error = new ChargeGatewayException(FailureType.UNKNOWN, "CODE", "오류");
        when(chargeGateway.fetch(PAYMENT_KEY)).thenThrow(error);

        assertThatThrownBy(() -> service.sync(PAYMENT_KEY)).isSameAs(error);
        verifyNotCharged();
    }
}
