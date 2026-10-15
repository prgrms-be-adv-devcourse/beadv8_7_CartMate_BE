package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.HoldingStatus;
import com.cartmate.cartmate.boundedContext.wallet.domain.Holding;
import com.cartmate.cartmate.boundedContext.wallet.out.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletSettlementUseCase {

    private final HoldingRepository holdingRepository;

    public void settle(List<Long> orderIds, Long settlementId) {

        if (settlementId == null || orderIds == null || orderIds.contains(null) || orderIds.isEmpty()) {
            throw new IllegalArgumentException("정산ID나 주문ID가 필요합니다.");
        }

        List<Holding> holdings = holdingRepository.findAllByOrderIdIn(orderIds)
                .stream()
                .filter(walletHolding -> walletHolding.getStatus() != HoldingStatus.CANCELED)
                .toList();

        if (holdings.isEmpty()) {
            throw new IllegalArgumentException("홀딩 내역이 없습니다.");
        }

        Set<Long> exist = holdings.stream()
                .map(Holding::getOrderId)
                .collect(Collectors.toSet());

        if (!exist.containsAll(orderIds)) {
            throw new IllegalArgumentException("홀딩이 없는 주문이 있습니다.");
        }

        for (Holding holding : holdings) {

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
