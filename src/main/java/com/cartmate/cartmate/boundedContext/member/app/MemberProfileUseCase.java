package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.dto.MemberProfileResponse;
import com.cartmate.cartmate.boundedContext.member.dto.UpdateProfileRequest;
import com.cartmate.cartmate.boundedContext.member.out.MemberAddressRepository;
import com.cartmate.cartmate.boundedContext.member.out.MemberRepository;
import com.cartmate.cartmate.boundedContext.member.out.PhoneVerificationStore;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberProfileUseCase {
    private final MemberSupport memberSupport;
    private final MemberRepository memberRepository;
    private final MemberAddressRepository memberAddressRepository;
    private final PhoneVerificationStore phoneVerificationStore;

    public MemberProfileResponse getProfile(Long memberId) {
        Member member = getMember(memberId);

        MemberProfileResponse.Address defaultAddress = memberAddressRepository
                .findFirstByMemberIdAndDefaultAddressTrue(memberId)
                .map(a -> new MemberProfileResponse.Address(a.getZipCode(), a.getBasicAddress(), a.getDetailAddress()))
                .orElse(null);

        return new MemberProfileResponse(
                member.getEmail(),
                member.getNickname(),
                member.getPhone(),
                member.getBirthDate(),
                member.getRole().name(),
                member.getModifyDate(),
                defaultAddress
        );
    }

    public MemberProfileResponse updateProfile(Long memberId, UpdateProfileRequest request) {
        Member member = getMember(memberId);

        String nickname = request.nickname().trim();
        String phone = request.phone().trim();

        if (!nickname.equals(member.getNickname())
                && memberRepository.existsByNicknameAndIdNot(nickname, memberId)) {
            throw new ServiceException("409-MEMBER_DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.");
        }

        boolean phoneChanged = !phone.equals(member.getPhone());
        if (phoneChanged) {
            boolean verified = phoneVerificationStore.find(phone)
                    .map(entry -> entry.isVerified(LocalDateTime.now()))
                    .orElse(false);
            if (!verified) {
                throw new ServiceException("400-MEMBER_PHONE_NOT_VERIFIED", "변경할 휴대폰 번호의 인증을 완료해주세요.");
            }
        }

        member.updateProfile(nickname, phone);

        try {
            memberRepository.flush(); // 변경 감지된 UPDATE를 바로 보내서 UNIQUE 위반을 여기서 잡음
        } catch (DataIntegrityViolationException e) {
            throw new ServiceException("409-MEMBER_DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.");
        }

        if (phoneChanged) {
            phoneVerificationStore.remove(phone);
        }

        return getProfile(memberId);
    }

    private Member getMember(Long memberId) {
        return memberSupport.findById(memberId)
                .orElseThrow(() -> new ServiceException("404-MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다."));
    }
}