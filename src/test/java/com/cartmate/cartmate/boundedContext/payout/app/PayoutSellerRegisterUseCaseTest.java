package com.cartmate.cartmate.boundedContext.payout.app;

import com.cartmate.cartmate.boundedContext.payout.domain.PayoutSeller;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutSellerRepository;
import com.cartmate.cartmate.shared.member.event.SellerRegisteredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayoutSellerRegisterUseCaseTest {

    private final PayoutSellerRepository payoutSellerRepository = mock(PayoutSellerRepository.class);
    private final PayoutSellerRegisterUseCase useCase = new PayoutSellerRegisterUseCase(payoutSellerRepository);

    private final SellerRegisteredEvent event = new SellerRegisteredEvent(10L, 20L, "BUSINESS", "팀바이");

    @Test
    @DisplayName("판매자 등록 이벤트를 받으면 판매자 ID와 회원 ID를 복제한다")
    void register() {
        when(payoutSellerRepository.existsById(10L)).thenReturn(false);

        useCase.register(event);

        ArgumentCaptor<PayoutSeller> captor = ArgumentCaptor.forClass(PayoutSeller.class);
        verify(payoutSellerRepository).save(captor.capture());
        assertThat(captor.getValue().getSellerId()).isEqualTo(10L);
        assertThat(captor.getValue().getMemberId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("이미 복제된 판매자는 다시 저장하지 않는다")
    void skipExisting() {
        when(payoutSellerRepository.existsById(10L)).thenReturn(true);

        useCase.register(event);

        verify(payoutSellerRepository, never()).save(any());
    }
}
