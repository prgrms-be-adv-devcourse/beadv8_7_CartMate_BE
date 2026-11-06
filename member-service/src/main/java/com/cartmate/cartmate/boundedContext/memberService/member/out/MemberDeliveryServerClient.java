package com.cartmate.cartmate.boundedContext.memberService.member.out;

import com.cartmate.cartmate.boundedContext.memberService.member.out.MyPageActivityClient.Delivery;

import java.util.List;

// 배송 서버(etc-service)에 묻는 회원별 조회. 배송 서버가 따로 떠 있어 HTTP로만 부른다.
public interface MemberDeliveryServerClient {

    List<Delivery> getMyDeliveries(Long memberId);

    long countUnfinishedOfBuyer(Long memberId);

    long countUnfinishedOfSharedOrders(List<Long> sharedGroupOrderIds);

    long countClaimsInProgressOfBuyer(Long memberId);

    long countUnfinishedOfSeller(Long sellerId);

    long countClaimsInProgressOfSeller(Long sellerId);
}
