package com.cartmate.cartmate.boundedContext.memberService.member.out;

import com.cartmate.cartmate.boundedContext.memberService.member.out.MyPageActivityClient.IndividualPurchase;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MyPageActivityClient.Order;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MyPageActivityClient.SharedOrder;
import com.cartmate.cartmate.boundedContext.memberService.member.out.MyPageActivityClient.SharedPurchase;

import java.util.List;

// 주문 서버(장바구니·공동구매·주문)에 묻는 회원별 조회. 주문 서버가 따로 떠 있어 HTTP로만 부른다.
public interface MemberOrderServerClient {

    List<SharedPurchase> getSharedPurchases(Long memberId);

    List<IndividualPurchase> getIndividualPurchases(Long memberId);

    List<Order> getOrders(Long memberId);

    List<SharedOrder> getSharedOrders(Long memberId);

    // 모집 중인 공동구매 수 (참여한 공동물품 + 개설한 개인물품)
    long countActiveGroupPurchases(Long memberId);

    long countPaymentPendingOrders(Long memberId);

    List<Long> findConfirmedSharedOrderIds(Long memberId);

    long countPayingSharedOrders(Long memberId);
}
