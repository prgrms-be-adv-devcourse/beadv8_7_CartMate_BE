package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseAccountLink;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseAccountLinkRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseMemberIdentityRepository;
import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseSignalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AbuseAccountRingSupportTest {

    @Mock
    private AbuseSignalRepository abuseSignalRepository;

    @Mock
    private AbuseMemberIdentityRepository abuseMemberIdentityRepository;

    @Mock
    private AbuseAccountLinkRepository abuseAccountLinkRepository;

    @InjectMocks
    private AbuseAccountRingSupport support;

    @Test
    @DisplayName("연결을 따라가 직접 · 간접으로 이어진 계정을 모두 무리로 모으고 자기 자신은 뺀다")
    void collectsTransitiveRing() {
        given(abuseAccountLinkRepository.findByMember(1L)).willReturn(List.of(link(1L, 2L)));
        given(abuseAccountLinkRepository.findByMember(2L)).willReturn(List.of(link(1L, 2L), link(2L, 3L)));
        given(abuseAccountLinkRepository.findByMember(3L)).willReturn(List.of(link(2L, 3L)));

        assertThat(support.ringMembersOf(1L)).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("무리가 아주 커져도 정해진 크기에서 멈춘다")
    void stopsAtMaxRingSize() {
        given(abuseAccountLinkRepository.findByMember(anyLong())).willAnswer(invocation -> {
            long member = invocation.getArgument(0);
            return List.of(link(member, member + 1));
        });

        assertThat(support.ringMembersOf(1L)).hasSize(AbuseAccountRingSupport.MAX_RING_SIZE - 1);
    }

    private static AbuseAccountLink link(long low, long high) {
        AbuseAccountLink link = BeanUtils.instantiateClass(AbuseAccountLink.class);
        ReflectionTestUtils.setField(link, "memberLowId", low);
        ReflectionTestUtils.setField(link, "memberHighId", high);
        return link;
    }
}
