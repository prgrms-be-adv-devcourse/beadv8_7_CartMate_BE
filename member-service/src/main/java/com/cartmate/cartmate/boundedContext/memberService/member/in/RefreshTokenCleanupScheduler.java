package com.cartmate.cartmate.boundedContext.memberService.member.in;

import com.cartmate.cartmate.boundedContext.memberService.member.app.MemberFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {
    private final MemberFacade memberFacade;

    // 매일 새벽 4시 만료된 리프레시 토큰과 신뢰 기기 정리
    @Scheduled(cron = "0 0 4 * * *")
    public void deleteExpired() {
        int tokens = memberFacade.deleteExpiredRefreshTokens();
        int devices = memberFacade.deleteExpiredTrustedDevices();
        log.info("만료된 리프레시 토큰 {}건, 신뢰 기기 {}건 삭제", tokens, devices);
    }
}
