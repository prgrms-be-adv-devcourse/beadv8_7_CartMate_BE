package com.cartmate.cartmate.boundedContext.memberService.member.in;

import com.cartmate.cartmate.boundedContext.memberService.member.app.MemberFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemberSuspensionScheduler {
    private final MemberFacade memberFacade;

    static final String LOCK_NAME = "member.releaseExpiredSuspensions";

    // 10분마다 기간이 지난 정지를 해제
    // 서버가 여러 대면 ShedLock으로 한 대만 돈다 (같은 회원을 두 번 해제해 상태 변경 이벤트가 두 번 나가지 않게).
    // 시계가 조금 어긋난 다른 서버가 곧바로 다시 돌지 않게 최소 30초는 잡아 둔다
    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void releaseExpiredSuspensions() {
        int released = memberFacade.releaseExpiredSuspensions();
        if (released > 0) {
            log.info("[회원 정지 자동 해제] {}건", released);
        }
    }
}