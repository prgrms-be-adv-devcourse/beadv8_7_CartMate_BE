package com.cartmate.cartmate.boundedContext.memberService.member.out;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// 마이페이지용 다른 모듈 조회는 여기서만 한다. 주문 서버는 분리되어 HTTP(MemberOrderServerClient)로 묻는다.
@Component
@RequiredArgsConstructor
public class MyPageActivityFacadeClient implements MyPageActivityClient {
    private final MemberPaymentServerClient paymentServerClient; // 예치금은 결제 서버
    private final MemberOrderServerClient orderServerClient; // 장바구니·공동구매·주문은 주문 서버
    private final MemberDeliveryServerClient deliveryServerClient; // 배송은 배송 서버(etc-service)

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