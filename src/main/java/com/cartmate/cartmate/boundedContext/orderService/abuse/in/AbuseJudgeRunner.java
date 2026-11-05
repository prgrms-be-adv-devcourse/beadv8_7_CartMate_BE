package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLTransientException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class AbuseJudgeRunner {

    private static final List<Class<? extends Throwable>> TRANSIENT_FAILURES = List.of(
            TransientDataAccessException.class,
            RecoverableDataAccessException.class,
            DataAccessResourceFailureException.class,
            CannotCreateTransactionException.class,
            SQLTransientException.class
    );

    private final AbuseFacade abuseFacade;

    AbuseJudgeOutcome judge(Long memberId) {
        try {
            abuseFacade.judgePending(memberId);
            return AbuseJudgeOutcome.JUDGED;
        } catch (Exception e) {
            if (isTransient(e)) {
                log.warn("일시 장애로 어뷰징 판정 보류, 실패로 세지 않고 다음 실행에서 재판정: memberId={}", memberId, e);
                return AbuseJudgeOutcome.TRANSIENT_FAILURE;
            }
            log.error("어뷰징 판정 실패, 재판정 대기: memberId={}", memberId, e);
            recordFailure(memberId, e);
            return AbuseJudgeOutcome.FAILURE;
        }
    }

    static boolean isTransient(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (isTransientType(cause)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTransientType(Throwable cause) {
        return TRANSIENT_FAILURES.stream().anyMatch(type -> type.isInstance(cause));
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
