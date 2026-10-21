package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.out.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberQueryAdapterTest {

    @Mock
    private MemberRepository memberRepository;

    private MemberQueryAdapter memberQuery;

    @BeforeEach
    void setUp() {
        memberQuery = new MemberQueryAdapter(memberRepository);
    }

    @Test
    @DisplayName("회원 ID별 닉네임을 한 번에 조회하고, 없는 회원은 null로 두며 중복·null ID는 제외한다")
    void findNicknames() {
        Member member = mock(Member.class);
        given(member.getId()).willReturn(1L);
        given(member.getNickname()).willReturn("철수");
        given(memberRepository.findAllById(List.of(1L, 99L))).willReturn(List.of(member));

        Map<Long, String> nicknames = memberQuery.findNicknames(Arrays.asList(1L, 99L, 1L, null));

        assertThat(nicknames).containsEntry(1L, "철수").containsEntry(99L, null).hasSize(2);
        verify(memberRepository).findAllById(List.of(1L, 99L)); // 회원마다가 아니라 한 번에 조회
    }

    @Test
    @DisplayName("빈 목록이면 빈 결과를 돌려준다")
    void emptyIds() {
        assertThat(memberQuery.findNicknames(List.of())).isEmpty();

    }
}