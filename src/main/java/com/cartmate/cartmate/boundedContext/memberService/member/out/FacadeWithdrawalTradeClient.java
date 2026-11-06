package com.cartmate.cartmate.boundedContext.memberService.member.out;

import com.cartmate.cartmate.boundedContext.etcService.delivery.app.DeliveryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// 각 모듈 Facade로 진행 중 거래 수를 모은다. 다른 모듈은 이 어댑터를 통해서만 부른다.
@Component
@RequiredArgsConstructor
public class FacadeWithdrawalTradeClient implements WithdrawalTradeClient {

    private final MemberOrderServerClient orderServerClient; // 장바구니·공동구매·주문은 주문 서버
    private final DeliveryFacade deliveryFacade;
    private final MemberPaymentServerClient paymentServerClient; // 정산은 결제 서버

    @Override
    public BuyerTrades getBuyerTrades(Long memberId) {
        // 확정된 공동 주문은 배송이 전부 구매확정·취소돼야 끝난 것 → 그 공동 주문들의 배송을 delivery에 묻는다
        List<Long> confirmedSharedOrderIds = orderServerClient.findConfirmedSharedOrderIds(memberId);
        long unconfirmed = deliveryFacade.countUnfinishedOfBuyer(memberId)
                + deliveryFacade.countUnfinishedOfSharedOrders(confirmedSharedOrderIds);

        return new BuyerTrades(
                orderServerClient.countPaymentPendingOrders(memberId),
                unconfirmed,
                orderServerClient.countPayingSharedOrders(memberId),
                deliveryFacade.countClaimsInProgressOfBuyer(memberId),
                orderServerClient.countActiveGroupPurchases(memberId));
    }

    @Override
    public SellerTrades getSellerTrades(Long sellerId) {
        return new SellerTrades(
                deliveryFacade.countUnfinishedOfSeller(sellerId),
                deliveryFacade.countClaimsInProgressOfSeller(sellerId),
                paymentServerClient.countPendingPayouts(sellerId));
    }
}