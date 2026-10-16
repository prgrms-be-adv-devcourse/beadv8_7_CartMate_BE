package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.HoldingStatus;
import com.cartmate.cartmate.boundedContext.wallet.domain.Holding;
import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.boundedContext.wallet.out.HoldingRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
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

        if (settlementId == null) {
            throw new ServiceException(WalletErrorCode.WALLET_SETTLEMENT_ID_REQUIRED);
        }

        if (orderIds == null || orderIds.isEmpty()) {
            throw new ServiceException(WalletErrorCode.WALLET_ORDER_IDS_REQUIRED);
        }

        if (orderIds.stream().anyMatch(orderId -> orderId == null)) {
            throw new ServiceException(WalletErrorCode.WALLET_ORDER_IDS_CONTAIN_NULL);
        }

        List<Holding> holdings = holdingRepository.findAllByOrderIdIn(orderIds)
                .stream()
                .filter(walletHolding -> walletHolding.getStatus() != HoldingStatus.CANCELED)
                .toList();

        if (holdings.isEmpty()) {
            throw new ServiceException(WalletErrorCode.WALLET_HOLDING_NOT_FOUND);
        }

        Set<Long> exist = holdings.stream()
                .map(Holding::getOrderId)
                .collect(Collectors.toSet());

        if (!exist.containsAll(orderIds)) {
            throw new ServiceException(WalletErrorCode.WALLET_SETTLEMENT_HOLDING_MISSING);
        }

        for (Holding holding : holdings) {

            if (holding.getStatus() == HoldingStatus.SETTLED) {
                if (!holding.getSettlementId().equals(settlementId)) {
                    throw new ServiceException(WalletErrorCode.WALLET_SETTLEMENT_CONFLICT);
                }
                continue;
            }

            holding.settle(settlementId);
        }

    }
}
