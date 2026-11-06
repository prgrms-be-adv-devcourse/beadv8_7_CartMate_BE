package com.cartmate.cartmate.boundedContext.memberService.member.out;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MyPageActivityFacadeClient implements MyPageActivityClient {
    private final MemberPaymentServerClient paymentServerClient; // 예치금은 결제 서버
    private final MemberOrderServerClient orderServerClient;
    private final MemberDeliveryServerClient deliveryServerClient;

    @Override
    public long getWalletBalance(Long memberId) {
        return paymentServerClient.getWalletBalance(memberId);
    }

    @Override
    public List<SharedPurchase> getSharedPurchases(Long memberId) {
        return orderServerClient.getSharedPurchases(memberId);
    }

    @Override
    public List<SharedOrder> getSharedOrders(Long memberId) {
        return orderServerClient.getSharedOrders(memberId);
    }

    @Override
    public List<IndividualPurchase> getIndividualPurchases(Long memberId) {
        return orderServerClient.getIndividualPurchases(memberId);
    }

    @Override
    public List<Order> getOrders(Long memberId) {
        return orderServerClient.getOrders(memberId);
    }

    @Override
    public List<Delivery> getDeliveries(Long memberId) {
        return deliveryServerClient.getMyDeliveries(memberId);
    }
}
