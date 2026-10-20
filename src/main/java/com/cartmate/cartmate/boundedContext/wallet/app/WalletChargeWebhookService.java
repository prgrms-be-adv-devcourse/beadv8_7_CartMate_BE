package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletChargeOrderInfo;
import com.cartmate.cartmate.boundedContext.wallet.domain.ChargeStatus;
import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.boundedContext.wallet.out.ChargeGatewayPayment;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletChargeGateway;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 결제사 웹훅으로 알게 된 결제 상태를 충전 주문에 반영한다. 결제사 호출이 끼어 있어서 트랜잭션을 직접 열지 않는다.
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletChargeWebhookService {

    private final WalletFacade walletFacade;
    private final WalletChargeGateway chargeGateway;
    private final WalletChargeSettlement settlement;

    // 웹훅 본문은 믿지 않고 결제키로 결제사에 다시 조회한다.
    // 결제사 조회가 실패하면 예외를 그대로 던져 결제사가 웹훅을 다시 보내게 한다.
    public void sync(String paymentKey) {
        ChargeGatewayPayment payment = chargeGateway.fetch(paymentKey);

        WalletChargeOrderInfo order;
        try {
            order = walletFacade.getChargeOrder(payment.orderNumber());
        } catch (ServiceException e) {
            if (WalletErrorCode.WALLET_CHARGE_ORDER_NOT_FOUND.resultCode().equals(e.getResultCode())) {
                log.info("충전 주문이 아닌 결제의 웹훅 무시: orderNumber={}", payment.orderNumber());
                return;
            }
            throw e;
        }

        // 이미 확정된 주문은 승인 API나 앞선 웹훅이 처리한 것이다.
        if (order.status() != ChargeStatus.PENDING) {
            return;
        }

        switch (payment.status()) {
            case DONE -> {
                try {
                    settlement.verifyApproved(order, paymentKey, payment);
                } catch (ServiceException e) {
                    return; // 불일치는 verifyApproved가 오류 로그를 남겼다. 다시 받아도 결과가 같으므로 재전송을 받지 않는다.
                }
                settlement.complete(order, paymentKey);
            }
            case CANCELED, FAILED -> settlement.failOrderOrThrow(order);
            default -> log.info("충전 결제 진행 중 웹훅: orderNumber={}, status={}", order.orderNumber(), payment.status());
        }
    }
}
