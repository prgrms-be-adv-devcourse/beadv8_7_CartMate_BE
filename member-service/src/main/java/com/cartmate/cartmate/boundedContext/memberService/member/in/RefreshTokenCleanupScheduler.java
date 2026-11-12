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
public class RefreshTokenCleanupScheduler {
    private final MemberFacade memberFacade;

    static final String LOCK_NAME = "member.deleteExpiredTokens";

    // 매일 새벽 4시(한국 시간) 만료된 리프레시 토큰과 신뢰 기기 정리
    // 서버 시간대가 UTC면 "4시"가 한국 시간 오후 1시가 되므로 시간대를 정해 둔다. 서버가 여러 대면 ShedLock으로 한 대만 돈다
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void deleteExpired() {
        int tokens = memberFacade.deleteExpiredRefreshTokens();
        int devices = memberFacade.deleteExpiredTrustedDevices();
        log.info("만료된 리프레시 토큰 {}건, 신뢰 기기 {}건 삭제", tokens, devices);
    }
}
