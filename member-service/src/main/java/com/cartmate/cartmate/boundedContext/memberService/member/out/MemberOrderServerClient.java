package com.cartmate.cartmate.boundedContext.memberService.member.out;

import com.cartmate.cartmate.boundedContext.memberService.member.out.MyPageActivityClient.IndividualPurchase;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MyPageActivityClient.Order;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MyPageActivityClient.SharedOrder;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MyPageActivityClient.SharedPurchase;

import java.util.List;

public interface MemberOrderServerClient {

    List<SharedPurchase> getSharedPurchases(Long memberId);

    List<IndividualPurchase> getIndividualPurchases(Long memberId);

    List<Order> getOrders(Long memberId);

    List<SharedOrder> getSharedOrders(Long memberId);

    long countActiveGroupPurchases(Long memberId);

    long countPaymentPendingOrders(Long memberId);

    List<Long> findConfirmedSharedOrderIds(Long memberId);

    long countPayingSharedOrders(Long memberId);
}
