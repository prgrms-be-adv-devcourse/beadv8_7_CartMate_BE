package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.domain.Seller;
import com.cartmate.cartmate.boundedContext.member.domain.SellerAccountFingerprint;
import com.cartmate.cartmate.boundedContext.member.domain.SellerType;
import com.cartmate.cartmate.boundedContext.member.dto.SellerRegisterRequest;
import com.cartmate.cartmate.boundedContext.member.out.AccountVerificationStore;
import com.cartmate.cartmate.boundedContext.member.out.MemberRepository;
import com.cartmate.cartmate.boundedContext.member.out.SellerRepository;
import com.cartmate.cartmate.global.eventPublisher.EventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemberSellerUseCaseTest {

    private static final Long MEMBER_ID = 7L;

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final SellerRepository sellerRepository = mock(SellerRepository.class);
    private final AccountVerificationStore store = mock(AccountVerificationStore.class);
    private final EventPublisher eventPublisher = mock(EventPublisher.class);
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final MemberSellerUseCase useCase =
            new MemberSellerUseCase(memberRepository, sellerRepository, store, eventPublisher, jwtProvider);

    private final SellerRegisterRequest request = new SellerRegisterRequest(
            SellerType.INDIVIDUAL, null, null, null, "010-1234-5678", "신한은행", "홍길동", "110123456789", true);
    private final String fingerprint =
            SellerAccountFingerprint.of(SellerType.INDIVIDUAL, "신한은행", "110123456789", "홍길동", null, null);

    @BeforeEach
    void setUp() {
        Member member = Member.create("a@a.com", "pw", "닉네임", "010-1234-5678", LocalDate.of(1990, 1, 2));
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(sellerRepository.saveAndFlush(any(Seller.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtProvider.createAccessToken(any(), anyString(), anyString(), any())).thenReturn("token");
    }

    private String resultCode(Throwable e) {
        return ((ServiceException) e).getResultCode();
    }

    @Test
    @DisplayName("계좌 확인 통과 표시가 없으면 400으로 거절하고 저장하지 않는다")
    void notVerified() {
        when(store.isVerified(MEMBER_ID, fingerprint)).thenReturn(false);

        assertThatThrownBy(() -> useCase.register(MEMBER_ID, request))
                .satisfies(e -> assertThat(resultCode(e)).isEqualTo("400-SELLER_ACCOUNT_NOT_VERIFIED"));
        verify(sellerRepository, never()).saveAndFlush(any());
        verify(store, never()).remove(anyLong());
    }

    @Test
    @DisplayName("확인한 입력값과 다른 계좌로 등록하면 400으로 거절한다")
    void changedAfterVerification() {
        when(store.isVerified(MEMBER_ID, fingerprint)).thenReturn(true);
        SellerRegisterRequest changed = new SellerRegisterRequest(
                SellerType.INDIVIDUAL, null, null, null, "010-1234-5678", "신한은행", "홍길동", "110999999999", true);

        assertThatThrownBy(() -> useCase.register(MEMBER_ID, changed))
                .satisfies(e -> assertThat(resultCode(e)).isEqualTo("400-SELLER_ACCOUNT_NOT_VERIFIED"));
        verify(sellerRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이미 다른 판매자가 등록한 계좌이면 409로 거절한다")
    void duplicateAccount() {
        when(store.isVerified(MEMBER_ID, fingerprint)).thenReturn(true);
        when(sellerRepository.existsByBankNameAndAccountNumber("신한은행", "110123456789")).thenReturn(true);

        assertThatThrownBy(() -> useCase.register(MEMBER_ID, request))
                .satisfies(e -> assertThat(resultCode(e)).isEqualTo("409-SELLER_ACCOUNT_DUPLICATE"));
        verify(sellerRepository, never()).saveAndFlush(any());
        verify(store, never()).remove(anyLong());
    }

    @Test
    @DisplayName("등록이 성공하면 판매자를 저장하고 통과 표시를 지운다")
    void registered() {
        when(store.isVerified(MEMBER_ID, fingerprint)).thenReturn(true);

        MemberSellerUseCase.RegisterResult result = useCase.register(MEMBER_ID, request);

        assertThat(result.accessToken()).isEqualTo("token");
        verify(sellerRepository).saveAndFlush(any(Seller.class));
        verify(store).remove(MEMBER_ID);
    }

    // 커밋과 롤백만 흉내 내는 트랜잭션 매니저. 스프링의 트랜잭션 동기화 흐름을 그대로 쓴다.
    private static class NoopTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private final TransactionTemplate transaction = new TransactionTemplate(new NoopTransactionManager());

    @Test
    @DisplayName("트랜잭션 안에서는 커밋이 끝난 뒤에 통과 표시를 지운다")
    void removeAfterCommit() {
        when(store.isVerified(MEMBER_ID, fingerprint)).thenReturn(true);

        transaction.executeWithoutResult(status -> {
            useCase.register(MEMBER_ID, request);
            verify(store, never()).remove(anyLong()); // 커밋 전에는 지우지 않는다
        });

        verify(store).remove(MEMBER_ID);
    }

    @Test
    @DisplayName("등록이 롤백되면 통과 표시를 지우지 않는다")
    void keepMarkWhenRolledBack() {
        when(store.isVerified(MEMBER_ID, fingerprint)).thenReturn(true);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            useCase.register(MEMBER_ID, request);
            throw new IllegalStateException("동기 이벤트 처리 실패");
        })).isInstanceOf(IllegalStateException.class);

        verify(store, never()).remove(anyLong());
    }

    @Test
    @DisplayName("커밋 뒤 Redis 삭제가 실패해도 등록은 성공으로 끝난다")
    void redisFailureAfterCommitDoesNotFailRegistration() {
        when(store.isVerified(MEMBER_ID, fingerprint)).thenReturn(true);
        doThrow(new RuntimeException("redis down")).when(store).remove(MEMBER_ID);

        transaction.executeWithoutResult(status -> useCase.register(MEMBER_ID, request));

        verify(store).remove(MEMBER_ID);
    }

    @Test
    @DisplayName("저장이 실패하면 통과 표시를 남겨 두어 다시 제출할 수 있다")
    void keepMarkWhenSaveFails() {
        when(store.isVerified(MEMBER_ID, fingerprint)).thenReturn(true);
        when(sellerRepository.saveAndFlush(any(Seller.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> useCase.register(MEMBER_ID, request))
                .satisfies(e -> assertThat(resultCode(e)).isEqualTo("409-SELLER_ALREADY_REGISTERED"));
        verify(store, never()).remove(anyLong());
    }
}
