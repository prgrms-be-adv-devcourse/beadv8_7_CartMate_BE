package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseIdentityCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseIdentityHasher;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseIdentityType;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseMemberIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class AbuseIdentityUseCase {

    private final AbuseMemberIdentityRepository abuseMemberIdentityRepository;

    void record(AbuseIdentityCommand command, LocalDateTime now) {
        if (command.memberId() == null) {
            return;
        }
        AbuseIdentityHasher.phone(command.receiverPhone()).ifPresent(hash ->
                abuseMemberIdentityRepository.insertIfAbsent(command.memberId(), AbuseIdentityType.PHONE.name(), hash, now));
        AbuseIdentityHasher.address(command.zipCode(), command.address(), command.addressDetail()).ifPresent(hash ->
                abuseMemberIdentityRepository.insertIfAbsent(command.memberId(), AbuseIdentityType.ADDRESS.name(), hash, now));
    }
}
