package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.Member;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.SignupRequest;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MemberRepository;
import com.cartmate.cartmate.boundedContext.memberService.member.out.PhoneVerificationStore;
import com.cartmate.cartmate.boundedContext.memberService.shared.member.event.MemberSignedUpEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.global.outbox.OutboxTopics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 가입 이벤트가 Outbox 발행 포트로 나가는지 (토픽 cartmate.member.member-signed-up, 키 = 회원 ID)
class MemberJoinUseCaseTest {

    private final MemberSupport memberSupport = mock(MemberSupport.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final PhoneVerificationStore phoneVerificationStore = mock(PhoneVerificationStore.class);

    private final MemberJoinUseCase useCase = new MemberJoinUseCase(
            memberSupport, memberRepository, passwordEncoder, eventPublisher, phoneVerificationStore);

    @Test
    void 가입하면_가입_이벤트를_Outbox_발행_포트로_보낸다() {
        SignupRequest request = new SignupRequest("new@cartmate.com", "Test1234!", "새회원", "010-1234-5678",
                LocalDate.of(1995, 1, 1));
        PhoneVerificationStore.Entry verified = mock(PhoneVerificationStore.Entry.class);
        when(verified.isVerified(any())).thenReturn(true);
        when(phoneVerificationStore.find(request.phone())).thenReturn(Optional.of(verified));
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 7L);
            return member;
        });

        useCase.join(request);

        ArgumentCaptor<MemberSignedUpEvent> captor = ArgumentCaptor.forClass(MemberSignedUpEvent.class);
        verify(eventPublisher).publish(captor.capture());
        MemberSignedUpEvent event = captor.getValue();
        assertThat(event.member().id()).isEqualTo(7L);
        assertThat(event.aggregateId()).isEqualTo("7");
        assertThat(OutboxTopics.of(MemberSignedUpEvent.class)).isEqualTo("cartmate.member.member-signed-up");
    }
}