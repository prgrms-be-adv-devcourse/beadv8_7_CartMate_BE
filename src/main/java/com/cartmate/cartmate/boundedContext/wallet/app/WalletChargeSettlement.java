package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletChargeOrderInfo;
import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayPayment;
import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayPayment.Status;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 결제사가 알려준 결제 결과를 충전 주문에 확정하는 공통 단계. 승인 API와 웹훅이 같이 쓴다.
// 결제사 호출이 앞에 있어서 트랜잭션을 직접 열지 않고, 충전과 실패 처리는 각자 WalletFacade의 트랜잭션에서 처리된다.
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletChargeSettlement {

    private final WalletFacade walletFacade;
    private final WalletRetryService walletRetryService;

    // 결제사가 알려준 결제가 우리 충전 주문의 결제인지 확인한다. 승인·취소 같은 상태와 무관하게 먼저 확인해야
    // 다른 주문의 결제 상태로 이 주문을 확정하지 않는다. 값이 다르면 승인된 결제가 남을 수 있으므로 오류 로그를 남긴다.
    public void verifyMatches(WalletChargeOrderInfo order, String paymentKey, ChargeGatewayPayment payment) {
        if (!order.orderNumber().equals(payment.orderNumber())
                || !order.amount().equals(payment.amount())
                || !paymentKey.equals(payment.paymentKey())) {
            log.error("결제사 결제와 충전 주문 불일치: orderNumber={}, orderAmount={}, paymentKey={}, "
                            + "paymentOrderNumber={}, paymentAmount={}, paymentPaymentKey={}, paymentStatus={}",
                    order.orderNumber(), order.amount(), paymentKey,
                    payment.orderNumber(), payment.amount(), payment.paymentKey(), payment.status());
            throw new ServiceException(WalletErrorCode.WALLET_CHARGE_PAYMENT_MISMATCH);
        }
    }

    // 결제사가 승인한 결과가 우리 충전 주문과 같은지 확인한다.
    public void verifyApproved(WalletChargeOrderInfo order, String paymentKey, ChargeGatewayPayment payment) {
        verifyMatches(order, paymentKey, payment);

        // 승인 완료(DONE)만 통과시킨다. 나중에 상태가 추가되어도 모르는 상태로는 충전하지 않는다.
        if (payment.status() != Status.DONE) {
            if (payment.status() == Status.CANCELED || payment.status() == Status.FAILED) {
                failOrder(order);
                throw new ServiceException(WalletErrorCode.WALLET_CHARGE_PAYMENT_REJECTED);
            }
            throw new ServiceException(WalletErrorCode.WALLET_CHARGE_PAYMENT_PROCESSING);
        }
    }

    public void complete(WalletChargeOrderInfo order, String paymentKey) {
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
    public void failOrder(WalletChargeOrderInfo order) {
        try {
            walletFacade.fail(order.walletId(), order.orderNumber());
        } catch (RuntimeException e) {
            log.warn("충전 주문 실패 처리 생략: orderNumber={}, reason={}", order.orderNumber(), e.getMessage());
        }
    }

    // 웹훅에서 쓰는 실패 처리. 반영에 실패하면 예외를 던져 결제사가 웹훅을 다시 보내게 한다.
    // 그 사이 다른 요청이 이미 확정한 주문(실패 처리할 수 없는 상태)만 정상으로 본다.
    public void failOrderOrThrow(WalletChargeOrderInfo order) {
        try {
            walletFacade.fail(order.walletId(), order.orderNumber());
        } catch (ServiceException e) {
            if (!WalletErrorCode.WALLET_CHARGE_NOT_FAILABLE.resultCode().equals(e.getResultCode())) {
                throw e;
            }
            log.info("이미 확정된 충전 주문이라 실패 처리 생략: orderNumber={}", order.orderNumber());
        }
    }
}
