package com.cartmate.cartmate.boundedContext.order.out;

import com.cartmate.cartmate.boundedContext.member.app.MemberFacade;
import com.cartmate.cartmate.shared.member.dto.MemberDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberFacadeOrderMemberClientTest {

    @Mock
    private MemberFacade memberFacade;

    private MemberFacadeOrderMemberClient memberClient;

    @BeforeEach
    void setUp() {
        memberClient = new MemberFacadeOrderMemberClient(memberFacade);
    }

    @Test
    @DisplayName("회원 ID별 닉네임을 찾고 없는 회원은 null로 둔다")
    void mapsNicknamesAndMissingToNull() {
        given(memberFacade.findById(1L)).willReturn(Optional.of(new MemberDto(1L, "a@test.com", "철수", "USER")));
        given(memberFacade.findById(99L)).willReturn(Optional.empty());

        Map<Long, String> nicknames = memberClient.findNicknames(List.of(1L, 99L, 1L));

        assertThat(nicknames).containsEntry(1L, "철수").containsEntry(99L, null).hasSize(2);
        verify(memberFacade, times(1)).findById(1L);
    }
}
