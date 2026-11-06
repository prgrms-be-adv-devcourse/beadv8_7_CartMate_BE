package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.PaybackPayout;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.PaybackPayoutStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.PaybackPayoutResponse;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.PaybackPayoutRepository;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.event.PaybackRequestedEvent;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositFailedEvent;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
class PaybackPayoutUseCase {

    private static final int MAX_LIST_SIZE = 100;

    private final PaybackPayoutRepository payoutRepository;
    private final DomainEventPublisher eventPublisher;

    // 달성 판정과 같은 트랜잭션: 판정이 저장되면 지급 행과 요청 이벤트(아웃박스)도 함께 남는다
    void requestAll(Long individualGroupPurchaseId, Collection<Long> memberIds, long amount, LocalDateTime now) {
        memberIds.stream().sorted().forEach(memberId -> {
            PaybackPayout payout = payoutRepository.save(
                    PaybackPayout.request(individualGroupPurchaseId, memberId, amount, now));
            eventPublisher.publish(toRequestedEvent(payout));
        });
    }

    void applyDeposited(PaybackDepositedEvent event) {
        payoutRepository.findByIdForUpdate(event.paybackId()).ifPresentOrElse(
                payout -> {
                    if (!payout.matches(event.memberId(), event.amount())) {
                        log.error("적립 결과가 지급 행과 다름, 반영하지 않음: paybackId={}, memberId={}/{}, amount={}/{}",
                                payout.getId(), payout.getMemberId(), event.memberId(), payout.getAmount(), event.amount());
                        return;
                    }
                    payout.markPaid(event.depositedAt());
                },
                () -> log.warn("적립 결과의 페이백 지급을 찾을 수 없음: paybackId={}", event.paybackId()));
    }

    void applyDepositFailed(PaybackDepositFailedEvent event) {
        payoutRepository.findByIdForUpdate(event.paybackId()).ifPresentOrElse(payout -> {
            if (payout.markFailed(event.failureCode(), event.retryable(), event.attempt(), event.failedAt()) && payout.isFailed()) {
                log.error("페이백 지급 실패 확정, 어드민 재요청 필요: paybackId={}, memberId={}, failureCode={}, attempt={}",
                        payout.getId(), payout.getMemberId(), payout.getFailureCode(), payout.getAttempt());
            }
        }, () -> log.warn("적립 실패 결과의 페이백 지급을 찾을 수 없음: paybackId={}", event.paybackId()));
    }

    List<Long> findRetryWaitingIds(int limit) {
        return payoutRepository.findIdsByStatus(PaybackPayoutStatus.RETRY_WAITING, Limit.of(limit));
    }

    // 스케줄러: 다른 실행이 먼저 처리했으면 대기 상태가 아니므로 건너뛴다
    void retryWaiting(Long paybackId, LocalDateTime now) {
        payoutRepository.findByIdForUpdate(paybackId)
                .filter(PaybackPayout::isRetryWaiting)
                .ifPresent(payout -> retry(payout, now));
    }

    void retryFailed(Long paybackId, LocalDateTime now) {
        PaybackPayout payout = payoutRepository.findByIdForUpdate(paybackId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_PAYBACK_NOT_FOUND));
        if (!payout.isFailed()) {
            throw new ServiceException(OrderErrorCode.ORDER_PAYBACK_NOT_FAILED);
        }
        retry(payout, now);
    }

    List<PaybackPayoutResponse> findAllByStatus(PaybackPayoutStatus status) {
        return payoutRepository.findAllByStatusOrderByIdDesc(status, Limit.of(MAX_LIST_SIZE)).stream()
                .map(PaybackPayoutResponse::from)
                .toList();
    }

    private void retry(PaybackPayout payout, LocalDateTime now) {
        payout.retry(now);
        eventPublisher.publish(toRequestedEvent(payout));
    }

    private static PaybackRequestedEvent toRequestedEvent(PaybackPayout payout) {
        return new PaybackRequestedEvent(payout.getId(), payout.getMemberId(), payout.getAmount(),
                payout.getIndividualGroupPurchaseId(), payout.getAttempt(), payout.getRequestedAt());
    }
}
