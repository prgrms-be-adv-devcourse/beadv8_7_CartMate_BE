package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformWalletProviderTest {

    private static final long PLATFORM_MEMBER_ID = 1L;

    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final PlatformWalletProvider provider = new PlatformWalletProvider(walletRepository, PLATFORM_MEMBER_ID);

    @Test
    @DisplayName("설정한 관리자 회원의 지갑이 있으면 그 지갑을 반환한다")
    void existing() {
        Wallet wallet = Wallet.createWallet(PLATFORM_MEMBER_ID);
        when(walletRepository.findByMemberId(PLATFORM_MEMBER_ID)).thenReturn(Optional.of(wallet));

        assertThat(provider.getPlatformWallet()).isSameAs(wallet);
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("지갑이 없으면 설정한 관리자 회원의 지갑을 만들어 저장한다")
    void createWhenMissing() {
        when(walletRepository.findByMemberId(PLATFORM_MEMBER_ID)).thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Wallet wallet = provider.getPlatformWallet();

        assertThat(wallet.getMemberId()).isEqualTo(PLATFORM_MEMBER_ID);
        assertThat(wallet.getBalance()).isZero();
        verify(walletRepository).save(wallet);
    }
}
