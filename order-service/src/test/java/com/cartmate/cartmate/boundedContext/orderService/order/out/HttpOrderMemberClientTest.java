package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.global.http.InternalApiClient;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class HttpOrderMemberClientTest {

    @Mock
    private InternalApiClient memberServerApiClient;

    private HttpOrderMemberClient memberClient;

    @BeforeEach
    void setUp() {
        memberClient = new HttpOrderMemberClient(memberServerApiClient);
    }

    @Test
    @DisplayName("중복 없는 회원 ID로 회원 서버에 닉네임을 묻고, 없는 회원의 null 값을 그대로 돌려준다")
    void asksMemberServerWithDistinctIds() {
        Map<Long, String> expected = new HashMap<>();
        expected.put(1L, "철수");
        expected.put(99L, null);
        given(memberServerApiClient.get(eq(HttpOrderMemberClient.PATH), eq(HttpOrderMemberClient.NICKNAMES), eq("1,99")))
                .willReturn(expected);

        Map<Long, String> nicknames = memberClient.findNicknames(List.of(1L, 99L, 1L));

        assertThat(nicknames).containsEntry(1L, "철수").containsEntry(99L, null).hasSize(2);
    }

    @Test
    @DisplayName("조회할 회원이 없으면 회원 서버를 부르지 않는다")
    void skipsCallForEmptyIds() {
        assertThat(memberClient.findNicknames(List.of())).isEmpty();

        then(memberServerApiClient).should(never()).get(any(), any(), any());
    }
}
