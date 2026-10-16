package com.cartmate.cartmate.boundedContext.wallet.in;

import com.cartmate.cartmate.boundedContext.wallet.app.WalletFacade;
import com.cartmate.cartmate.shared.member.event.MemberSignedUpEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletEventListener {

    private final WalletFacade walletFacade;

    // 회원가입 이벤트를 받아 지갑을 생성한다. 생성 실패 시 회원가입도 함께 롤백된다.
    @EventListener
    public void handle(MemberSignedUpEvent event) {
        walletFacade.createWallet(event.member().id());
    }
}
