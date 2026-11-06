package com.cartmate.cartmate.boundedContext.paymentService.wallet.in;

import com.cartmate.cartmate.boundedContext.paymentService.wallet.app.WalletFacade;
import com.cartmate.cartmate.shared.member.event.MemberSignedUpEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 가입 이벤트를 받아 지갑을 만든다. 회원 서비스와 다른 앱이라 Kafka로 받는다 (PaymentServiceKafkaConfig).
 * Kafka 수신 다리가 processed_event 기록과 같은 트랜잭션에서 발행하므로 {@code @EventListener}로 그 트랜잭션에 함께 들어간다.
 * 같은 메시지가 두 번 와도 지갑은 하나 (이미 있으면 건너뜀 + member_id 유니크).
 */
@Component
@RequiredArgsConstructor
public class WalletEventListener {

    private final WalletFacade walletFacade;

    // 가입과는 다른 서비스라 지갑 생성이 실패해도 가입은 롤백되지 않는다. Kafka가 다시 보낸다
    @EventListener
    public void handle(MemberSignedUpEvent event) {
        walletFacade.createWallet(event.member().id());
    }
}
