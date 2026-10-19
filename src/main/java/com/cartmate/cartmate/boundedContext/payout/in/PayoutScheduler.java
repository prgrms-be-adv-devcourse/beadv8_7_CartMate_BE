package com.cartmate.cartmate.boundedContext.payout.in;

import com.cartmate.cartmate.boundedContext.payout.app.PayoutFacade;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutScheduler {

    private final PayoutRepository payoutRepository;
    private final PayoutFacade payoutFacade;

    // 매일 지급 예정 시각이 지난 대기 정산을 지급한다. 지급 예정일이 매월 15일이라 그날 처음 지급되고, 실패한 건은 다음 날 다시 시도한다.
    @Scheduled(cron = "${custom.payout.cron:0 10 0 * * *}")
    public void payDue() {
        List<Long> ids = payoutRepository.findIdsByStatusAndScheduledAtBefore(PayoutStatus.PENDING, LocalDateTime.now());
        payEach(ids);
    }

    // 건마다 별도 트랜잭션이라 한 건이 실패해도 나머지는 계속 처리한다.
    private void payEach(List<Long> ids) {
        for (Long id : ids) {
            try {
                payoutFacade.pay(id);
            } catch (Exception e) {
                log.error("정산 지급 실패: payoutId={}", id, e);
            }
        }
    }
}
