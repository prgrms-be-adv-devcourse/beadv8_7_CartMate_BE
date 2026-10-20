package com.cartmate.cartmate.boundedContext.wallet.domain;

import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

// 정산 금액을 홀딩들의 남은 금액 비율로 나눈다.
public final class HoldingSettlementAllocator {

    private HoldingSettlementAllocator() {
    }

    // 정산 금액(amount)을 홀딩들의 남은 금액(remainings) 비율로 나눠 홀딩별로 꺼낼 금액을 반환한다. 반환 순서는 remainings와 같다.
    // 1) 몫 = amount x 홀딩의 남은 금액 / 남은 금액 합계를 내림해서 구한다.
    // 2) 내림 때문에 남은 금액은 남은 금액이 큰 홀딩부터 1원씩 더한다. 어떤 홀딩도 자기 남은 금액을 넘지 않는다.
    // 정산 금액이 0 이하이면 WALLET_INVALID_AMOUNT, 남은 금액 합계가 정산 금액보다 작으면 WALLET_HOLDING_INSUFFICIENT 예외가 발생한다.
    public static List<Long> allocate(List<Long> remainings, long amount) {
        if (amount <= 0) {
            throw new ServiceException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }

        long total = remainings.stream().mapToLong(Long::longValue).sum();

        if (total < amount) {
            throw new ServiceException(WalletErrorCode.WALLET_HOLDING_INSUFFICIENT);
        }

        long[] shares = new long[remainings.size()];
        long allocated = 0;

        for (int i = 0; i < shares.length; i++) {
            shares[i] = Math.multiplyExact(amount, remainings.get(i)) / total;
            allocated += shares[i];
        }

        List<Integer> order = IntStream.range(0, shares.length).boxed()
                .sorted(Comparator.comparing((Integer i) -> remainings.get(i)).reversed())
                .toList();

        long remainder = amount - allocated;

        while (remainder > 0) {
            for (int index : order) {
                if (remainder == 0) {
                    break;
                }
                if (shares[index] < remainings.get(index)) {
                    shares[index]++;
                    remainder--;
                }
            }
        }

        List<Long> result = new ArrayList<>();
        for (long share : shares) {
            result.add(share);
        }
        return result;
    }
}
