package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.exception.MemberErrorCode;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.Member;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.SignupRequest;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MemberRepository;
import com.cartmate.cartmate.boundedContext.memberService.member.out.PhoneVerificationStore;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.member.dto.MemberDto;
import com.cartmate.cartmate.shared.member.event.MemberSignedUpEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberJoinUseCase {
    private final MemberSupport memberSupport;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    // 가입과 같은 트랜잭션에 Outbox 행을 남기고, 같은 앱 안의 기존 리스너용 Spring 이벤트도 함께 발행한다
    private final DomainEventPublisher eventPublisher;
    private final PhoneVerificationStore phoneVerificationStore;
    private final MemberEmailVerificationUseCase emailVerification;

    public MemberDto join(SignupRequest request) {
        LocalDateTime now = LocalDateTime.now();

        if (!phoneVerificationStore.isVerified(request.phone(), Instant.now())) {
            throw new ServiceException(MemberErrorCode.MEMBER_PHONE_NOT_VERIFIED);
        }
        if (!emailVerification.isVerified(request.email(), Instant.now())) {
            throw new ServiceException(MemberErrorCode.MEMBER_EMAIL_NOT_VERIFIED);
        }
        if (memberSupport.existsByEmail(request.email())) {
            throw new ServiceException(MemberErrorCode.MEMBER_DUPLICATE_EMAIL);
        }
        if (memberSupport.existsByNickname(request.nickname())) {
            throw new ServiceException(MemberErrorCode.MEMBER_DUPLICATE_NICKNAME);
        }

        Member member = Member.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                request.phone(),
                request.birthDate()
        );
        member.markEmailVerified(now);

        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new ServiceException(MemberErrorCode.MEMBER_DUPLICATE);
        }

        phoneVerificationStore.remove(request.phone());
        emailVerification.consume(request.email());
        MemberDto memberDto = member.toDto();
        eventPublisher.publish(new MemberSignedUpEvent(memberDto));
        return memberDto;
    }

}