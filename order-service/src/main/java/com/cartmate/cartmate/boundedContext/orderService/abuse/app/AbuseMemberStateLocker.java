package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseMemberState;
import com.cartmate.cartmate.boundedContext.orderService.abuse.exception.AbuseErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseMemberStateRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
class AbuseMemberStateLocker {

    private final AbuseMemberStateRepository abuseMemberStateRepository;

    AbuseMemberState lock(Long memberId, LocalDateTime now) {
        abuseMemberStateRepository.insertIfAbsent(memberId, now);
        return abuseMemberStateRepository.findForUpdate(memberId)
                .orElseThrow(() -> new ServiceException(AbuseErrorCode.ABUSE_MEMBER_STATE_NOT_FOUND));
    }
}
