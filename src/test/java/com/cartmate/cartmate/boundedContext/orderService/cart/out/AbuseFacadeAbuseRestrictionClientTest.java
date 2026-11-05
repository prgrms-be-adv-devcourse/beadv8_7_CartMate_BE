package com.cartmate.cartmate.boundedContext.orderService.cart.out;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.shared.abuse.dto.AbuseRestrictionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AbuseFacadeAbuseRestrictionClientTest {

    @Mock
    private AbuseFacade abuseFacade;

    @InjectMocks
    private AbuseFacadeAbuseRestrictionClient client;

    @Test
    @DisplayName("조회에 성공하면 abuse 결과를 그대로 돌려준다")
    void returnsRestriction() {
        AbuseRestrictionStatus restricted = AbuseRestrictionStatus.restrictedUntil(1L, LocalDateTime.of(2099, 1, 1, 0, 0));
        given(abuseFacade.getRestriction(1L)).willReturn(restricted);

        assertThat(client.getRestriction(1L)).isEqualTo(restricted);
    }

    @Test
    @DisplayName("조회가 실패하면 공동구매를 막지 않도록 제한 없음으로 돌려준다")
    void failsOpen() {
        given(abuseFacade.getRestriction(1L)).willThrow(new IllegalStateException("abuse DB down"));

        assertThat(client.getRestriction(1L)).isEqualTo(AbuseRestrictionStatus.none(1L));
    }
}
