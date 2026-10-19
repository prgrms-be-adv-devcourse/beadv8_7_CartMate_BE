package com.cartmate.cartmate.boundedContext.payout.in.batch;

import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Queue;

// 지급 예정 시각이 지난 대기 정산의 ID를 페이지 단위로 읽는다.
// 처리된 정산은 대기 상태에서 빠져 OFFSET 페이징을 쓰면 건너뛰게 되므로, 마지막으로 읽은 ID 다음부터 읽는다.
public class PayoutDueReader implements ItemReader<Long> {

    private final PayoutRepository payoutRepository;
    private final LocalDateTime now;
    private final int pageSize;

    private final Queue<Long> buffer = new ArrayDeque<>();
    private Long lastId = 0L;
    private boolean exhausted;

    public PayoutDueReader(PayoutRepository payoutRepository, LocalDateTime now, int pageSize) {
        this.payoutRepository = payoutRepository;
        this.now = now;
        this.pageSize = pageSize;
    }

    @Override
    public @Nullable Long read() {
        if (buffer.isEmpty() && !exhausted) {
            fill();
        }

        Long id = buffer.poll();
        if (id != null) {
            lastId = id;
        }
        return id;
    }

    private void fill() {
        buffer.addAll(payoutRepository.findDueIdsAfter(
                PayoutStatus.PENDING, now, lastId, PageRequest.of(0, pageSize)));

        if (buffer.size() < pageSize) {
            exhausted = true;
        }
    }
}
