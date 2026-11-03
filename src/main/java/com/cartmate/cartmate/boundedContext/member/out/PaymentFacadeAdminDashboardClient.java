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
        PaymentDailySummary summary = paymentFacade.getDailySummary(date);
            return new PaymentStats(summary.paidCount(), summary.paidAmount(), summary.refundCount(), summary.refundAmount());
    }
}