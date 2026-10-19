package com.cartmate.cartmate.boundedContext.payout.app;

import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import com.cartmate.cartmate.shared.member.event.SellerRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PayoutFacade {

    private final PayoutCreateUseCase payoutCreateUseCase;
    private final PayoutSellerRegisterUseCase payoutSellerRegisterUseCase;
    private final PayoutPayUseCase payoutPayUseCase;

    // 구매확정 이벤트를 받아 정산을 만든다. 구매확정과 같은 트랜잭션에 참여해 정산 생성이 실패하면 구매확정도 롤백된다.
    @Transactional
    public void createPayouts(DeliveryConfirmedEvent event) {
        payoutCreateUseCase.create(event);
    }

    // 정산 한 건을 지급한다. 입금과 지급 완료 처리가 한 트랜잭션이라 입금이 실패하면 지급 완료도 롤백된다.
    @Transactional
    public void pay(Long payoutId) {
        payoutPayUseCase.pay(payoutId, LocalDateTime.now());
    }

    // 판매자 등록 이벤트를 받아 지급에 필요한 판매자의 회원 ID를 복제한다.
    @Transactional
    public void registerSeller(SellerRegisteredEvent event) {
        payoutSellerRegisterUseCase.register(event);
    }
}
