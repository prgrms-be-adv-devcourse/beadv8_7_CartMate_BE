package com.cartmate.cartmate.boundedContext.member.in;

import com.cartmate.cartmate.boundedContext.member.app.MemberFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberSuspensionScheduler {
    private final MemberFacade memberFacade;

    // 10분마다 기간이 지난 정지를 해제
    @Scheduled(cron = "0 */10 * * * *")
    public void releaseExpiredSuspensions() {
        int released = memberFacade.releaseExpiredSuspensions();
        if (released > 0) {
            log.info("[회원 정지 자동 해제] {}건", released);
        }
    }
}