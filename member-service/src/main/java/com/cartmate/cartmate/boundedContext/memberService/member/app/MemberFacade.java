package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.Member;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.SuspensionReleaseType;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.*;
import com.cartmate.cartmate.global.page.PageResponse;
import com.cartmate.cartmate.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// member 모듈의 정문. Controller와 다른 모듈은 여기만 호출한다.
@Service
@RequiredArgsConstructor
public class MemberFacade {
    private final MemberSupport memberSupport;
    private final MemberJoinUseCase memberJoinUseCase;
    private final MemberSendPhoneCodeUseCase memberSendPhoneCodeUseCase;
    private final MemberVerifyPhoneCodeUseCase memberVerifyPhoneCodeUseCase;
    private final MemberEmailVerificationUseCase memberEmailVerificationUseCase;
    private final MemberProfileUseCase memberProfileUseCase;
    private final MemberChangePasswordUseCase memberChangePasswordUseCase;
    private final AdminMemberSearchUseCase adminMemberSearchUseCase;
    private final AdminMemberDetailUseCase adminMemberDetailUseCase;
    private final AdminMemberStatusUseCase adminMemberStatusUseCase;
    private final MemberSellerUseCase memberSellerUseCase;
    private final MemberSellerAccountVerifyUseCase memberSellerAccountVerifyUseCase;
    private final MyPageDashboardUseCase myPageDashboardUseCase;
    private final MemberTokenUseCase memberTokenUseCase;
    private final MemberAddressUseCase memberAddressUseCase;
    private final AdminDashboardUseCase adminDashboardUseCase;
    private final MemberWithdrawalUseCase memberWithdrawalUseCase;


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

    // 트랜잭션 없음: 메일 발송(SMTP) 동안 DB 연결을 잡지 않는다
    public int sendEmailCode(String email) {
        return memberEmailVerificationUseCase.send(email, Instant.now());
    }

    public void verifyEmailCode(String email, String code) {
        memberEmailVerificationUseCase.verify(email, code, Instant.now());
    }

    public int sendPhoneCode(String phone) {
        return memberSendPhoneCodeUseCase.send(phone);
    }

    public void verifyPhoneCode(String phone, String code) {
        memberVerifyPhoneCodeUseCase.verify(phone, code);
    }

    private final MemberLoginUseCase memberLoginUseCase;
    private final MemberLoginMfaUseCase memberLoginMfaUseCase;
    private final TrustedDeviceUseCase trustedDeviceUseCase;

    // 트랜잭션 없음: 2차 인증 메일(SMTP)을 보내는 동안 DB 연결을 잡지 않는다 (토큰·기기 저장은 저장소가 각자 연다)
    // trustedDeviceToken: 쿠키의 신뢰 기기 토큰 (없으면 null)
    public LoginOutcome login(String email, String password, boolean remember, String trustedDeviceToken) {
        return memberLoginUseCase.login(email, password, remember, trustedDeviceToken, Instant.now());
    }

    @Transactional
    public LoginOutcome verifyLoginMfa(String challengeId, String code, boolean trustDevice, String deviceName) {
        return memberLoginMfaUseCase.verify(challengeId, code, trustDevice, deviceName, Instant.now());
    }

    public int resendLoginMfa(String challengeId) {
        return memberLoginMfaUseCase.resend(challengeId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<TrustedDeviceResponse> getTrustedDevices(Long memberId) {
        return trustedDeviceUseCase.list(memberId, LocalDateTime.now()).stream().map(TrustedDeviceResponse::from).toList();
    }

    @Transactional
    public void revokeTrustedDevice(Long memberId, Long deviceId) {
        trustedDeviceUseCase.revoke(memberId, deviceId);
    }

    @Transactional
    public void revokeAllTrustedDevices(Long memberId) {
        trustedDeviceUseCase.revokeAll(memberId);
    }

    @Transactional
    public int deleteExpiredTrustedDevices() {
        return trustedDeviceUseCase.deleteExpired(LocalDateTime.now());
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

    // 외부 호출이 나가므로 DB 트랜잭션 밖에서 실행한다.
    public void verifySellerAccount(Long memberId, SellerAccountVerifyRequest request) {
        memberSellerAccountVerifyUseCase.verify(memberId, request);
    }

    @Transactional
    public MemberSellerUseCase.RegisterResult registerSeller(Long memberId, SellerRegisterRequest request) {
        return memberSellerUseCase.register(memberId, request);
    }

    @Transactional(readOnly = true)
    public List<MemberAddressResponse> getAddresses(Long memberId) {
        return memberAddressUseCase.list(memberId);
    }

    @Transactional
    public MemberAddressResponse createAddress(Long memberId, MemberAddressRequest request) {
        return memberAddressUseCase.create(memberId, request);
    }

    @Transactional
    public MemberAddressResponse updateAddress(Long memberId, Long addressId, MemberAddressRequest request) {
        return memberAddressUseCase.update(memberId, addressId, request);
    }

    @Transactional
    public MemberAddressResponse makeDefaultAddress(Long memberId, Long addressId) {
        return memberAddressUseCase.makeDefault(memberId, addressId);
    }

    @Transactional
    public void deleteAddress(Long memberId, Long addressId) {
        memberAddressUseCase.delete(memberId, addressId);
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getAdminDashboard() {
        return adminDashboardUseCase.getDashboard();
    }

    // ===== 회원 탈퇴 =====
    @Transactional(readOnly = true)
    public WithdrawalCheckResponse checkWithdrawal(Long memberId) {
        return memberWithdrawalUseCase.check(memberId);
    }

    @Transactional
    public WithdrawalCheckResponse requestWithdrawal(Long memberId, String password) {
        return memberWithdrawalUseCase.request(memberId, password);
    }

    @Transactional
    public WithdrawalCheckResponse cancelWithdrawal(Long memberId) {
        return memberWithdrawalUseCase.cancel(memberId);
    }

    @Transactional(readOnly = true)
    public List<Long> findWithdrawalPendingMemberIds() {
        return memberWithdrawalUseCase.findPendingMemberIds();
    }
}