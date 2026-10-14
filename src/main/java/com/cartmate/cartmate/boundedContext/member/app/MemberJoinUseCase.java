package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.dto.SignupRequest;
import com.cartmate.cartmate.boundedContext.member.out.MemberRepository;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.member.dto.MemberDto;
import com.cartmate.cartmate.shared.member.event.MemberSignedUpEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberJoinUseCase {
    private final MemberSupport memberSupport;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;

    public MemberDto join(SignupRequest request) {
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

        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new ServiceException("409-MEMBER_DUPLICATE", "이미 사용 중인 이메일 또는 닉네임입니다.");
        }

        MemberDto memberDto = member.toDto();
        eventPublisher.publish(new MemberSignedUpEvent(memberDto));
        return memberDto;
    }
}