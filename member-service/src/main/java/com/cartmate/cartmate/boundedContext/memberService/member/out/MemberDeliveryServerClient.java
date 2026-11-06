package com.cartmate.cartmate.boundedContext.memberService.member.out;

import com.cartmate.cartmate.boundedContext.memberService.member.out.MyPageActivityClient.Delivery;

import java.util.List;

public interface MemberDeliveryServerClient {

    List<Delivery> getMyDeliveries(Long memberId);

    long countUnfinishedOfBuyer(Long memberId);

    long countUnfinishedOfSharedOrders(List<Long> sharedGroupOrderIds);

    long countClaimsInProgressOfBuyer(Long memberId);

    long countUnfinishedOfSeller(Long sellerId);

    long countClaimsInProgressOfSeller(Long sellerId);
}
