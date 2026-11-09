package com.cartmate.cartmate.boundedContext.post.in;

import com.cartmate.cartmate.boundedContext.post.app.SalesPostFacade;
import com.cartmate.cartmate.shared.member.event.MemberWithdrawalRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesPostMemberEventListener {

    private final SalesPostFacade salesPostFacade;

    // 판매자가 탈퇴를 신청하면 판매 중인 판매글을 전부 판매 중지한다.
    // 판매 중지는 기존 흐름(상품 판매 중지 + 숨김 이벤트)을 그대로 타서, 모집 중 공동구매 정리까지 따라온다.
    // 탈퇴 신청이 커밋된 뒤 별도 스레드에서, 판매글 하나마다 따로 처리한다 (하나 실패해도 나머지 진행).
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MemberWithdrawalRequestedEvent event) {
        if (event.sellerId() == null) {
            return;
        }
        for (Long postId : salesPostFacade.findOnSalePostIdsOfSeller(event.sellerId())) {
            try {
                salesPostFacade.pauseSalesPostBySeller(postId, event.sellerId(), event.memberId());
            } catch (Exception e) {
                log.error("판매자 #{} 탈퇴 신청: 판매글 #{} 판매 중지 실패", event.sellerId(), postId, e);
            }
        }
    }
}