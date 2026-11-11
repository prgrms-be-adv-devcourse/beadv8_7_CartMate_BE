package com.cartmate.cartmate.boundedContext.memberService.member.app;

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
        boolean phoneVerified = phoneVerificationStore.find(request.phone())
                .map(entry -> entry.isVerified(now))
                .orElse(false);
        if (!phoneVerified) {
            throw new ServiceException("400-MEMBER_PHONE_NOT_VERIFIED", "휴대폰 인증을 완료해주세요.");
        }
        if (!emailVerification.isVerified(request.email(), Instant.now())) {
            throw new ServiceException("400-MEMBER_EMAIL_NOT_VERIFIED", "이메일 인증을 완료해주세요.");
        }
        if (memberSupport.existsByEmail(request.email())) {
            throw new ServiceException("409-MEMBER_DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다.");
        }
        if (memberSupport.existsByNickname(request.nickname())) {
            throw new ServiceException("409-MEMBER_DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.");
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
            throw new ServiceException("409-MEMBER_DUPLICATE", "이미 사용 중인 이메일 또는 닉네임입니다.");
        }

        phoneVerificationStore.remove(request.phone());
        emailVerification.consume(request.email());
        MemberDto memberDto = member.toDto();
        eventPublisher.publish(new MemberSignedUpEvent(memberDto));
        return memberDto;
    }

}