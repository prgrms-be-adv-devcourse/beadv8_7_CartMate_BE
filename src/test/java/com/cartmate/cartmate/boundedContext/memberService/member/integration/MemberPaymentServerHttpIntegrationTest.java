package com.cartmate.cartmate.boundedContext.memberService.member.integration;

import com.cartmate.cartmate.boundedContext.memberService.member.out.HttpMemberPaymentServerClient;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MemberPaymentServerClient;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MemberPaymentServerClient.PaymentStats;
import com.cartmate.cartmate.boundedContext.paymentService.payment.app.PaymentFacade;
import com.cartmate.cartmate.boundedContext.paymentService.payment.dto.PaymentDailySummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * http 모드에서 회원 모듈이 결제 서버(결제·예치금·정산)를 내부 API로 부르는지,
 * 그리고 HTTP로 받은 값이 같은 앱 Facade 값과 같은지 확인한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"cartmate.scheduling.enabled=false", "cartmate.payment.client=http"})
@ActiveProfiles("test")
class MemberPaymentServerHttpIntegrationTest {

    @Autowired
    private MemberPaymentServerClient memberPaymentServerClient;

    @Autowired
    private PaymentFacade paymentFacade;

    @Test
    void http_모드면_HTTP_구현을_쓴다() {
        assertThat(memberPaymentServerClient).isInstanceOf(HttpMemberPaymentServerClient.class);
    }

    @Test
    void 일별_결제_집계를_HTTP로_받아도_Facade와_같다() {
        LocalDate today = LocalDate.now();
        PaymentDailySummary expected = paymentFacade.getDailySummary(today);

        PaymentStats actual = memberPaymentServerClient.getDailyPaymentStats(today);

        assertThat(actual).isEqualTo(new PaymentStats(
                expected.paidCount(), expected.paidAmount(), expected.refundCount(), expected.refundAmount()));
    }

    @Test
    void 정산이_없는_판매자의_지급_전_정산은_0건() {
        assertThat(memberPaymentServerClient.countPendingPayouts(-1L)).isZero();
    }
}