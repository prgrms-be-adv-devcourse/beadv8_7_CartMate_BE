package com.cartmate.cartmate.boundedContext.member.out;

import com.cartmate.cartmate.boundedContext.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.boundedContext.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.boundedContext.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.wallet.app.WalletFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

// 마이페이지용 다른 모듈 조회는 여기서만 한다. MSA로 나누면 이 클래스만 HTTP 호출로 교체한다.
@Component
@RequiredArgsConstructor
public class MyPageActivityFacadeClient implements MyPageActivityClient {
    private static final int DELIVERY_PAGE_SIZE = 100;

    private final WalletFacade walletFacade;
    private final SharedGroupPurchaseFacade sharedGroupPurchaseFacade;
    private final SharedGroupOrderFacade sharedGroupOrderFacade;
    private final IndividualGroupPurchaseFacade individualGroupPurchaseFacade;
    private final OrderFacade orderFacade;
    private final DeliveryFacade deliveryFacade;

    @Override
    public long getWalletBalance(Long memberId) {
        Long balance = walletFacade.getWallet(memberId).balance();
        return balance == null ? 0L : balance;
    }

    @Override
    public List<SharedPurchase> getSharedPurchases(Long memberId) {
        return sharedGroupPurchaseFacade.getMyList(memberId).stream()
                .map(p -> new SharedPurchase(p.id(), p.title(), p.status().name(),
                        p.participantCount(), p.maxParticipants(), p.editDeadlineAt()))
                .toList();
    }

    @Override
    public List<SharedOrder> getSharedOrders(Long memberId) {
        return sharedGroupOrderFacade.getMyList(memberId).stream()
                .map(o -> new SharedOrder(o.id(), o.sharedGroupPurchaseId(), o.status().name(), o.myShareAmount(),
                        o.myPaymentStatus().name(), o.paidCount(), o.participantCount(), o.paymentDeadlineAt()))
                .toList();
    }

    @Override
    public List<IndividualPurchase> getIndividualPurchases(Long memberId) {
        return individualGroupPurchaseFacade.getMyList(memberId).stream()
                .map(p -> new IndividualPurchase(p.id(), p.title(), p.open(), p.productCount(), p.deadlineAt()))
                .toList();
    }

    @Override
    public List<Order> getOrders(Long memberId) {
        return orderFacade.getMyList(memberId).stream()
                .map(o -> new Order(o.id(), o.orderNumber(), o.orderType().name(), o.status().name(),
                        o.totalAmount(), o.itemCount(), o.firstProductName(), o.createdAt()))
                .toList();
    }

    @Override
    public List<Delivery> getDeliveries(Long memberId) {
        return deliveryFacade.getMyDeliveries(memberId, PageRequest.of(0, DELIVERY_PAGE_SIZE)).getContent().stream()
                .map(d -> new Delivery(d.orderId(), d.purchaseType().name(), d.status().name(), d.createdAt()))
                .toList();
    }
}