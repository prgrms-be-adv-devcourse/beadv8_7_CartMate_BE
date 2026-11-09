package com.cartmate.cartmate.boundedContext.member.out;

import com.cartmate.cartmate.boundedContext.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.order.app.personal.OrderFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// 각 모듈 Facade로 진행 중 거래 수를 모은다. 다른 모듈은 이 어댑터를 통해서만 부른다.
@Component
@RequiredArgsConstructor
public class FacadeWithdrawalTradeClient implements WithdrawalTradeClient {

    private final OrderFacade orderFacade;
    private final SharedGroupOrderFacade sharedGroupOrderFacade;
    private final DeliveryFacade deliveryFacade;
    private final SharedGroupPurchaseFacade sharedGroupPurchaseFacade;
    private final IndividualGroupPurchaseFacade individualGroupPurchaseFacade;
    private final MemberPaymentServerClient paymentServerClient; // 정산은 결제 서버

    @Override
    public BuyerTrades getBuyerTrades(Long memberId) {
        // 확정된 공동 주문은 배송이 전부 구매확정·취소돼야 끝난 것 → 그 공동 주문들의 배송을 delivery에 묻는다
        List<Long> confirmedSharedOrderIds = sharedGroupOrderFacade.findConfirmedIdsOfParticipant(memberId);
        long unconfirmed = deliveryFacade.countUnfinishedOfBuyer(memberId)
                + deliveryFacade.countUnfinishedOfSharedOrders(confirmedSharedOrderIds);

        return new BuyerTrades(
                orderFacade.countPaymentPendingOfBuyer(memberId),
                unconfirmed,
                sharedGroupOrderFacade.findPayingIdsOfParticipant(memberId).size(),
                deliveryFacade.countClaimsInProgressOfBuyer(memberId),
                sharedGroupPurchaseFacade.countActiveOfMember(memberId)
                        + individualGroupPurchaseFacade.countOpenOfCreator(memberId));
    }

    @Override
    public SellerTrades getSellerTrades(Long sellerId) {
        return new SellerTrades(
                deliveryFacade.countUnfinishedOfSeller(sellerId),
                deliveryFacade.countClaimsInProgressOfSeller(sellerId),
                paymentServerClient.countPendingPayouts(sellerId));
    }
}