package com.cartmate.cartmate.boundedContext.member.in;

import com.cartmate.cartmate.boundedContext.member.domain.MemberStatus;
import com.cartmate.cartmate.boundedContext.member.out.MemberRepository;
import com.cartmate.cartmate.global.security.BlockedMemberRegistry;
import com.cartmate.cartmate.shared.member.event.MemberStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 정지 회원 차단 목록을 DB와 맞춘다
@Component
@RequiredArgsConstructor
public class BlockedMemberSync {
    private final MemberRepository memberRepository;
    private final BlockedMemberRegistry blockedMemberRegistry;

    // 서버 시작 시 DB의 정지 회원을 불러온다
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        blockedMemberRegistry.replaceAll(memberRepository.findIdsByStatus(MemberStatus.SUSPENDED));
    }

    // 정지·해제가 커밋된 뒤에 반영한다 (롤백되면 반영하지 않음)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MemberStatusChangedEvent event) {
        if ("SUSPENDED".equals(event.status())) {
            blockedMemberRegistry.block(event.memberId());
        } else {
            blockedMemberRegistry.unblock(event.memberId());
        }
    }
}