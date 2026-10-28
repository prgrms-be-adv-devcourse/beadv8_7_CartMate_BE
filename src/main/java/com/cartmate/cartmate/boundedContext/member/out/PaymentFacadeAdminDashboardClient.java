package com.cartmate.cartmate.boundedContext.member.out;

import com.cartmate.cartmate.boundedContext.payment.app.PaymentFacade;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentDailySummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class PaymentFacadeAdminDashboardClient implements AdminDashboardPaymentClient {
    private final PaymentFacade paymentFacade;

    @Override
    public PaymentStats getDailyStats(LocalDate date) {
        PaymentDailySummary s = paymentFacade.getDailySummary(date);
        return new PaymentStats(s.paidCount(), s.paidAmount(), s.refundCount(), s.refundAmount());
    }
}