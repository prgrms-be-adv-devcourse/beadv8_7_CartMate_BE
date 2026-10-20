package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.domain.SuspensionReleaseType;
import com.cartmate.cartmate.boundedContext.member.dto.*;
import com.cartmate.cartmate.global.page.PageResponse;
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
    private final MemberSendPhoneCodeUseCase memberSendPhoneCodeUseCase;
    private final MemberVerifyPhoneCodeUseCase memberVerifyPhoneCodeUseCase;
    private final MemberProfileUseCase memberProfileUseCase;
    private final MemberChangePasswordUseCase memberChangePasswordUseCase;
    private final AdminMemberSearchUseCase adminMemberSearchUseCase;
    private final AdminMemberDetailUseCase adminMemberDetailUseCase;
    private final AdminMemberStatusUseCase adminMemberStatusUseCase;
    private final MemberSellerUseCase memberSellerUseCase;
    private final MyPageDashboardUseCase myPageDashboardUseCase;
    private final MemberTokenUseCase memberTokenUseCase;

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

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !memberSupport.existsByEmail(email);
    }

    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String nickname) {
        return !memberSupport.existsByNickname(nickname);
    }

    public int sendPhoneCode(String phone) {
        return memberSendPhoneCodeUseCase.send(phone);
    }

    public void verifyPhoneCode(String phone, String code) {
        memberVerifyPhoneCodeUseCase.verify(phone, code);
    }

    private final MemberLoginUseCase memberLoginUseCase;

    @Transactional
    public LoginResult login(String email, String password, boolean remember) {
        return memberLoginUseCase.login(email, password, remember);
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        return memberTokenUseCase.refresh(refreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        memberTokenUseCase.revoke(refreshToken);
    }

    @Transactional
    public int deleteExpiredRefreshTokens() {
        return memberTokenUseCase.deleteExpired();
    }


    //임시 조회용
    @Transactional(readOnly = true)
    public MyPageDashboardResponse getMyPageDashboard(Long memberId) {
        return myPageDashboardUseCase.getDashboard(memberId);
    }

    @Transactional(readOnly = true)
    public MemberProfileResponse getProfile(Long memberId) {
        return memberProfileUseCase.getProfile(memberId);
    }

    @Transactional
    public MemberProfileResponse updateProfile(Long memberId, UpdateProfileRequest request) {
        return memberProfileUseCase.updateProfile(memberId, request);
    }

    @Transactional
    public void changePassword(Long memberId, ChangePasswordRequest request) {
        memberChangePasswordUseCase.changePassword(memberId, request);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminMemberResponse> searchMembersForAdmin(AdminMemberSearchRequest condition, int page, int size) {
        return adminMemberSearchUseCase.search(condition, page, size);
    }
    @Transactional(readOnly = true)
    public AdminMemberDetailResponse getMemberDetailForAdmin(Long memberId) {
        return adminMemberDetailUseCase.get(memberId);
    }

    @Transactional
    public void suspendMember(Long adminId, Long memberId, AdminSuspendRequest request) {
        adminMemberStatusUseCase.suspend(adminId, memberId, request);
    }

    @Transactional
    public void activateMember(Long memberId) {
        adminMemberStatusUseCase.activate(memberId, SuspensionReleaseType.MANUAL);
    }

    @Transactional
    public AdminBulkStatusResponse changeMemberStatusBulk(Long adminId, AdminBulkStatusRequest request) {
        return adminMemberStatusUseCase.bulk(adminId, request);
    }

    @Transactional
    public int releaseExpiredSuspensions() {
        return adminMemberStatusUseCase.releaseExpired();
    }

    @Transactional(readOnly = true)
    public Optional<SellerResponse> getMySeller(Long memberId) {
        return memberSellerUseCase.getMySeller(memberId);
    }

    @Transactional(readOnly = true)
    public boolean isBusinessNumberAvailable(String businessNumber) {
        return memberSellerUseCase.isBusinessNumberAvailable(businessNumber);
    }

    @Transactional
    public MemberSellerUseCase.RegisterResult registerSeller(Long memberId, SellerRegisterRequest request) {
        return memberSellerUseCase.register(memberId, request);
    }
}