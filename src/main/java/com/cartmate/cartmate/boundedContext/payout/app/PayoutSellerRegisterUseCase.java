package com.cartmate.cartmate.boundedContext.payout.app;

import com.cartmate.cartmate.boundedContext.payout.domain.PayoutSeller;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutSellerRepository;
import com.cartmate.cartmate.shared.member.event.SellerRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PayoutSellerRegisterUseCase {

    private final PayoutSellerRepository payoutSellerRepository;

    // 등록된 판매자의 회원 ID를 복제해 둔다. 이미 복제된 판매자는 건너뛰어 같은 이벤트가 다시 와도 안전하다.
    public void register(SellerRegisteredEvent event) {
        if (payoutSellerRepository.existsById(event.sellerId())) {
            return;
        }

        payoutSellerRepository.save(PayoutSeller.create(event.sellerId(), event.memberId()));
    }
}
