package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.HoldingStatus;
import com.cartmate.cartmate.boundedContext.wallet.domain.Holding;
import com.cartmate.cartmate.boundedContext.wallet.out.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletSettlementUseCase {

    private final HoldingRepository holdingRepository;

    public void settle(List<Long> orderIds, Long settlementId) {

        if (settlementId == null || orderIds == null || orderIds.contains(null) || orderIds.isEmpty()) {
            throw new IllegalArgumentException("정산ID나 주문ID가 필요합니다.");
        }

        List<Holding> existing = holdingRepository.findAllByOrderIdIn(orderIds);

        for (Holding holding : existing) {

            if (holding.getStatus() == HoldingStatus.SETTLED) {
                if (!holding.getSettlementId().equals(settlementId)) {
                    throw new IllegalArgumentException("다른 정산으로 이미 정산된 홀딩입니다.");
                }
                continue;
            }
            holding.settle(settlementId);
        }

    }
}
