package com.cartmate.cartmate.boundedContext.abuse.in;

import com.cartmate.cartmate.boundedContext.abuse.app.AbuseFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AbuseJudgeRunner {

    private final AbuseFacade abuseFacade;

    boolean judge(Long memberId) {
        try {
            abuseFacade.judgePending(memberId);
            return true;
        } catch (Exception e) {
            log.error("어뷰징 판정 실패, 재판정 대기: memberId={}", memberId, e);
            recordFailure(memberId, e);
            return false;
        }
    }

    private void recordFailure(Long memberId, Exception cause) {
        try {
            abuseFacade.recordJudgeFailure(memberId, describe(cause));
        } catch (Exception e) {
            log.error("어뷰징 판정 실패 기록 실패: memberId={}", memberId, e);
        }
    }

    private String describe(Exception cause) {
        String name = cause.getClass().getSimpleName();
        return cause.getMessage() == null ? name : name + ": " + cause.getMessage();
    }
}
