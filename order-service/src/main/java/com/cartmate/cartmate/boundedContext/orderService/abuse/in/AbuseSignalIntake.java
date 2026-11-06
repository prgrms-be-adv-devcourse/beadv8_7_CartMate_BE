package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
class AbuseSignalIntake {

    private final AbuseFacade abuseFacade;
    private final AbuseJudgeRunner abuseJudgeRunner;

    void receiveAndJudge(List<AbuseSignalCommand> commands, String source) {
        commands.forEach(abuseFacade::receive);
        List<Long> memberIds = commands.stream().map(AbuseSignalCommand::memberId).distinct().toList();
        for (Long memberId : memberIds) {
            if (abuseJudgeRunner.judge(memberId) == AbuseJudgeOutcome.TRANSIENT_FAILURE) {
                log.warn("일시 장애로 판정을 멈춤, 남은 회원은 재판정 워커가 처리: source={}", source);
                return;
            }
        }
    }

    static Optional<AbuseSignalCommand> command(Long memberId, AbuseSignalType type, String sourceKey,
                                                LocalDateTime occurredAt) {
        try {
            return Optional.of(new AbuseSignalCommand(memberId, type, sourceKey, occurredAt));
        } catch (ServiceException e) {
            log.warn("잘못된 어뷰징 신호라 접수하지 않음: type={}, sourceKey={}, memberId={}", type, sourceKey, memberId);
            return Optional.empty();
        }
    }
}
