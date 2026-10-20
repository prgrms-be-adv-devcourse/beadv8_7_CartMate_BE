package com.cartmate.cartmate.boundedContext.cart.out;

import com.cartmate.cartmate.shared.member.MemberQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberFacadeMemberClientTest {

    @Mock
    private MemberQuery memberQuery;

    private MemberFacadeMemberClient memberClient;

    @BeforeEach
    void setUp() {
        memberClient = new MemberFacadeMemberClient(memberQuery);
    }

    @Test
    @DisplayName("닉네임 조회를 member의 MemberQuery에 맡기고 결과를 그대로 돌려준다")
    void delegatesToMemberQuery() {
        Map<Long, String> expected = new HashMap<>();
        expected.put(1L, "철수");
        expected.put(99L, null);
        given(memberQuery.findNicknames(List.of(1L, 99L, 1L))).willReturn(expected);

        Map<Long, String> nicknames = memberClient.findNicknames(List.of(1L, 99L, 1L));

        assertThat(nicknames).containsEntry(1L, "철수").containsEntry(99L, null).hasSize(2);
    }
}