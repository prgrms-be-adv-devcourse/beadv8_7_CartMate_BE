package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.dto.SignupRequest;
import com.cartmate.cartmate.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// member 모듈의 정문. Controller와 다른 모듈은 여기만 호출한다.
@Service
@RequiredArgsConstructor
public class MemberFacade {
    private final MemberSupport memberSupport;
    private final MemberJoinUseCase memberJoinUseCase;

    @Transactional
    public MemberDto join(SignupRequest request) {
        return memberJoinUseCase.join(request);
    }

    @Transactional(readOnly = true)
    public Optional<MemberDto> findById(Long id) {
        return memberSupport.findById(id).map(Member::toDto);
    }

    @Transactional(readOnly = true)
    public long count() {
        return memberSupport.count();
    }
}