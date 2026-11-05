package com.cartmate.cartmate.boundedContext.memberService.member.in;

import com.cartmate.cartmate.boundedContext.memberService.member.app.MemberFacade;
import com.cartmate.cartmate.global.security.WithdrawalPendingRegistry;
import com.cartmate.cartmate.shared.member.event.MemberWithdrawalCanceledEvent;
import com.cartmate.cartmate.shared.member.event.MemberWithdrawalRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 탈퇴 대기 회원 목록을 DB와 맞춘다 (정지 회원 BlockedMemberSync와 같은 방식)
@Component
@RequiredArgsConstructor
public class WithdrawalPendingSync {

    private final MemberFacade memberFacade;
    private final WithdrawalPendingRegistry withdrawalPendingRegistry;

    // 서버 시작 시 DB의 탈퇴 대기 회원을 불러온다
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        withdrawalPendingRegistry.replaceAll(memberFacade.findWithdrawalPendingMemberIds());
    }

    // 신청·취소가 커밋된 뒤에 반영한다 (롤백되면 반영하지 않음)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MemberWithdrawalRequestedEvent event) {
        withdrawalPendingRegistry.add(event.memberId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MemberWithdrawalCanceledEvent event) {
        withdrawalPendingRegistry.remove(event.memberId());
    }
}