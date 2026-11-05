package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.BankCode;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.Member;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.SellerAccountFingerprint;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.SellerAccountProperties;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.SellerType;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.SellerAccountVerifyRequest;
import com.cartmate.cartmate.boundedContext.memberService.member.out.AccountVerificationStore;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MemberRepository;
import com.cartmate.cartmate.boundedContext.memberService.member.out.account.AccountVerifier;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberSellerAccountVerifyUseCaseTest {

    private static final Long MEMBER_ID = 7L;
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final AccountVerifier accountVerifier = mock(AccountVerifier.class);
    private final AccountVerificationStore store = mock(AccountVerificationStore.class);
    private final MemberSellerAccountVerifyUseCase useCase = new MemberSellerAccountVerifyUseCase(
            memberRepository, accountVerifier, store, new SellerAccountProperties(TTL, 5, WINDOW));

    @BeforeEach
    void setUp() {
        Member member = Member.create("a@a.com", "pw", "닉네임", "010-1234-5678", LocalDate.of(1990, 1, 2));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(store.tryAcquireAttempt(eq(MEMBER_ID), anyInt(), any())).thenReturn(true);
    }

    private SellerAccountVerifyRequest individual(String holder, String accountNumber) {
        return new SellerAccountVerifyRequest(SellerType.INDIVIDUAL, null, null, null, "신한은행", holder, accountNumber);
    }

    private SellerAccountVerifyRequest business(String businessName, String representativeName, String businessNumber) {
        return new SellerAccountVerifyRequest(SellerType.BUSINESS, businessName, representativeName, businessNumber,
                "신한은행", "홍길동", "110123456789");
    }

    private String resultCode(Throwable e) {
        return ((ServiceException) e).getResultCode();
    }

    @Test
    @DisplayName("개인은 회원 생년월일과 입력한 예금주로 확인하고 통과 표시를 저장한다")
    void individualMatched() {
        when(accountVerifier.isHolderMatched(BankCode.SHINHAN, "110123456789", "900102", List.of("홍길동"))).thenReturn(true);

        useCase.verify(MEMBER_ID, individual("홍길동", "110123456789"));

        String fingerprint = SellerAccountFingerprint.of(SellerType.INDIVIDUAL, "신한은행", "110123456789", "홍길동", null, null, null);
        verify(store).saveVerified(MEMBER_ID, fingerprint, TTL);
    }

    @Test
    @DisplayName("사업자는 하이픈 없는 사업자번호로 조회하고 상호명 또는 대표자명이면 통과한다")
    void businessMatched() {
        when(accountVerifier.isHolderMatched(BankCode.SHINHAN, "110123456789", "1234567890", List.of("(주)팀바이", "김대표")))
                .thenReturn(true);

        useCase.verify(MEMBER_ID, business("(주)팀바이", "김대표", "123-45-67890"));

        String fingerprint = SellerAccountFingerprint.of(SellerType.BUSINESS, "신한은행", "110123456789", "홍길동",
                "(주)팀바이", "김대표", "123-45-67890");
        verify(store).saveVerified(MEMBER_ID, fingerprint, TTL);
    }

    @Test
    @DisplayName("불일치하면 400으로 거절하고 통과 표시를 저장하지 않는다")
    void mismatched() {
        when(accountVerifier.isHolderMatched(any(), anyString(), anyString(), anyList())).thenReturn(false);

        assertThatThrownBy(() -> useCase.verify(MEMBER_ID, individual("홍길동", "110123456789")))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(resultCode(e)).isEqualTo("400-SELLER_ACCOUNT_MISMATCH"));
        verify(store, never()).saveVerified(any(), any(), any());
    }

    @Test
    @DisplayName("호출 실패는 그대로 올려 보내고 통과 표시를 저장하지 않는다")
    void unavailable() {
        when(accountVerifier.isHolderMatched(any(), anyString(), anyString(), anyList()))
                .thenThrow(new ServiceException("503-SELLER_ACCOUNT_VERIFY_UNAVAILABLE", "일시 장애"));

        assertThatThrownBy(() -> useCase.verify(MEMBER_ID, individual("홍길동", "110123456789")))
                .satisfies(e -> assertThat(resultCode(e)).isEqualTo("503-SELLER_ACCOUNT_VERIFY_UNAVAILABLE"));
        verify(store, never()).saveVerified(any(), any(), any());
    }

    @Test
    @DisplayName("호출 횟수를 넘으면 429로 거절하고 은행을 호출하지 않는다")
    void tooManyRequests() {
        when(store.tryAcquireAttempt(eq(MEMBER_ID), anyInt(), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.verify(MEMBER_ID, individual("홍길동", "110123456789")))
                .satisfies(e -> assertThat(resultCode(e)).isEqualTo("429-SELLER_ACCOUNT_VERIFY_TOO_MANY_REQUESTS"));
        verify(accountVerifier, never()).isHolderMatched(any(), anyString(), anyString(), anyList());
    }

    @Test
    @DisplayName("사업자인데 상호명이 비면 400이고 횟수도 세지 않는다")
    void businessNameRequired() {
        assertThatThrownBy(() -> useCase.verify(MEMBER_ID, business(" ", "김대표", "123-45-67890")))
                .satisfies(e -> assertThat(resultCode(e)).isEqualTo("400-SELLER_BUSINESS_INFO_REQUIRED"));
        verify(store, never()).tryAcquireAttempt(any(), anyInt(), any());
    }

    @Test
    @DisplayName("사업자인데 대표자명이 비면 400이고 횟수도 세지 않는다")
    void representativeNameRequired() {
        assertThatThrownBy(() -> useCase.verify(MEMBER_ID, business("(주)팀바이", null, "123-45-67890")))
                .satisfies(e -> assertThat(resultCode(e)).isEqualTo("400-SELLER_BUSINESS_INFO_REQUIRED"));
        verify(store, never()).tryAcquireAttempt(any(), anyInt(), any());
    }

    @Test
    @DisplayName("지원하지 않는 은행이면 400이고 횟수도 세지 않는다")
    void unsupportedBank() {
        SellerAccountVerifyRequest request = new SellerAccountVerifyRequest(
                SellerType.INDIVIDUAL, null, null, null, "없는은행", "홍길동", "110123456789");

        assertThatThrownBy(() -> useCase.verify(MEMBER_ID, request))
                .satisfies(e -> assertThat(resultCode(e)).isEqualTo("400-SELLER_BANK_NOT_SUPPORTED"));
        verify(store, never()).tryAcquireAttempt(any(), anyInt(), any());
    }

    @Test
    @DisplayName("회원이 없으면 404이다")
    void memberNotFound() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.verify(99L, individual("홍길동", "110123456789")))
                .satisfies(e -> assertThat(resultCode(e)).isEqualTo("404-MEMBER_NOT_FOUND"));
    }
}
