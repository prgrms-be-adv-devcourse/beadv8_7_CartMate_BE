package com.cartmate.cartmate.boundedContext.payout.in.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

// 지급에 실패해 건너뛴 정산을 기록한다. 건너뛴 정산은 대기 상태로 남아 다음 실행 때 다시 시도된다.
@Slf4j
@Component
public class PayoutSkipListener implements SkipListener<Long, Long> {

    @Override
    public void onSkipInRead(Throwable t) {
        log.error("정산 지급 대상 조회 실패", t);
    }

    @Override
    public void onSkipInWrite(Long payoutId, Throwable t) {
        log.error("정산 지급 실패: payoutId={}", payoutId, t);
    }
}
