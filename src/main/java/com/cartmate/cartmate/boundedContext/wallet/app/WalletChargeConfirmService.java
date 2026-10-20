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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 충전 승인 순서를 조율한다. 결제사 호출이 끼어 있어서 트랜잭션을 직접 열지 않는다.
// 충전 주문 조회와 충전 완료는 각자 WalletFacade의 트랜잭션에서 처리되고, 결제사 호출은 트랜잭션 밖에서 한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletChargeConfirmService {

    private final WalletFacade walletFacade;
    private final WalletRetryService walletRetryService;
    private final WalletChargeGateway chargeGateway;

    // 1) 요청을 충전 주문과 대조한다. 2) 결제사에 승인을 요청한다. 3) 승인 결과가 충전 주문과 같은지 확인한다. 4) 충전을 완료한다.
    // 이미 완료된 주문은 같은 결제 키일 때 결제사를 호출하지 않고 정상 종료한다.
    public void confirm(Long walletId, String orderNumber, String paymentKey, Long amount) {
        WalletChargeOrderInfo order = walletFacade.getChargeOrder(orderNumber);

        verifyRequest(order, walletId, paymentKey, amount);

        if (order.status() == ChargeStatus.COMPLETED) {
            return;
        }

        ChargeGatewayPayment payment = approve(order, paymentKey);

        verifyApproved(order, paymentKey, payment);

        complete(order, paymentKey);
    }

    // 결제사를 호출하기 전에 끝낼 수 있는 검증이다. 클라이언트가 보낸 금액은 주문 금액과 같아야 한다.
    private void verifyRequest(WalletChargeOrderInfo order, Long walletId, String paymentKey, Long amount) {
        if (!order.walletId().equals(walletId)) {
            throw new ServiceException(WalletErrorCode.WALLET_ACCESS_DENIED);
        }

        if (paymentKey == null || paymentKey.isBlank()) {
            throw new ServiceException(WalletErrorCode.WALLET_PAYMENT_KEY_REQUIRED);
        }

        if (!order.amount().equals(amount)) {
            throw new ServiceException(WalletErrorCode.WALLET_CHARGE_AMOUNT_MISMATCH);
        }

        if (order.status() == ChargeStatus.COMPLETED) {
            if (!paymentKey.equals(order.paymentKey())) {
                throw new ServiceException(WalletErrorCode.WALLET_PAYMENT_KEY_MISMATCH);
            }
            return;
        }

        if (order.status() != ChargeStatus.PENDING) {
            throw new ServiceException(WalletErrorCode.WALLET_CHARGE_NOT_COMPLETABLE);
        }
    }

    // 결제사에 승인을 요청한다. 승인 금액은 클라이언트가 보낸 값이 아니라 충전 주문의 금액으로 보낸다.
    private ChargeGatewayPayment approve(WalletChargeOrderInfo order, String paymentKey) {
        try {
            return chargeGateway.confirm(paymentKey, order.orderNumber(), order.amount(), order.idempotencyKey());
        } catch (ChargeGatewayException e) {
            if (e.getFailureType() == FailureType.REJECTED) {
                log.info("충전 결제 승인 거절: orderNumber={}, code={}", order.orderNumber(), e.getCode());
                failOrder(order);
                throw new ServiceException(WalletErrorCode.WALLET_CHARGE_PAYMENT_REJECTED);
            }

            // 이미 처리된 결제이거나 처리 여부를 알 수 없으므로 실패로 확정하지 않고 결제사에 실제 상태를 조회한다.
            log.warn("충전 결제 승인 결과 불명확: orderNumber={}, type={}, code={}",
                    order.orderNumber(), e.getFailureType(), e.getCode());
            return fetch(order, paymentKey);
        }
    }

    // 조회마저 실패하면 충전 주문은 대기 상태로 두고, 이후 재요청이나 웹훅이 결과를 확정하게 한다.
    private ChargeGatewayPayment fetch(WalletChargeOrderInfo order, String paymentKey) {
        try {
            return chargeGateway.fetch(paymentKey);
        } catch (ChargeGatewayException e) {
            log.warn("충전 결제 조회 실패: orderNumber={}, type={}, code={}",
                    order.orderNumber(), e.getFailureType(), e.getCode());
            throw new ServiceException(WalletErrorCode.WALLET_CHARGE_PAYMENT_PROCESSING);
        }
    }

    // 결제사가 승인한 결과가 우리 충전 주문과 같은지 확인한다.
    private void verifyApproved(WalletChargeOrderInfo order, String paymentKey, ChargeGatewayPayment payment) {
        // 승인 완료(DONE)만 통과시킨다. 나중에 상태가 추가되어도 모르는 상태로는 충전하지 않는다.
        if (payment.status() != Status.DONE) {
            if (payment.status() == Status.CANCELED || payment.status() == Status.FAILED) {
                failOrder(order);
                throw new ServiceException(WalletErrorCode.WALLET_CHARGE_PAYMENT_REJECTED);
            }
            throw new ServiceException(WalletErrorCode.WALLET_CHARGE_PAYMENT_PROCESSING);
        }

        // 결제는 승인됐는데 값이 다르면 충전하지 않는다. 승인된 결제가 남으므로 확인할 수 있게 오류 로그를 남긴다.
        if (!order.orderNumber().equals(payment.orderNumber())
                || !order.amount().equals(payment.amount())
                || !paymentKey.equals(payment.paymentKey())) {
            log.error("승인된 결제와 충전 주문 불일치: orderNumber={}, orderAmount={}, paymentKey={}, "
                            + "approvedOrderNumber={}, approvedAmount={}, approvedPaymentKey={}",
                    order.orderNumber(), order.amount(), paymentKey,
                    payment.orderNumber(), payment.amount(), payment.paymentKey());
            throw new ServiceException(WalletErrorCode.WALLET_CHARGE_PAYMENT_MISMATCH);
        }
    }

    private void complete(WalletChargeOrderInfo order, String paymentKey) {
        try {
            walletRetryService.charge(order.walletId(), order.orderNumber(), paymentKey, order.amount());
        } catch (RuntimeException e) {
            // 결제사는 승인했는데 충전 반영에 실패한 경우다. 결제 키를 남겨 두어야 나중에 확인할 수 있다.
            log.error("결제 승인 후 충전 반영 실패: orderNumber={}, paymentKey={}", order.orderNumber(), paymentKey, e);
            throw e;
        }
    }

    // 확정 실패한 충전 주문을 실패 처리한다. 동시에 다른 요청이 먼저 처리했거나 락이 충돌해도
    // 호출하는 쪽에는 결제 거절 오류를 그대로 주기 위해 실패 처리 오류는 삼킨다.
    private void failOrder(WalletChargeOrderInfo order) {
        try {
            walletFacade.fail(order.walletId(), order.orderNumber());
        } catch (RuntimeException e) {
            log.warn("충전 주문 실패 처리 생략: orderNumber={}, reason={}", order.orderNumber(), e.getMessage());
        }
    }
}
