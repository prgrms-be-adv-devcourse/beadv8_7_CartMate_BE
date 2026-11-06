package com.cartmate.cartmate.boundedContext.orderService.cart.out.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;


public interface SharedGroupPurchaseRepository extends JpaRepository<SharedGroupPurchase, Long>, SharedGroupPurchaseRepositoryCustom {

    boolean existsByInviteCode(String inviteCode);

    // 판매글 판매 중지 대상: 해당 상품이 담긴 모집 중(편집 가능·보류) 공동구매
    @Query("select distinct g.id from SharedGroupPurchase g join g.items i " +
            "where g.status in (com.cartmate.cartmate.shared.cart.SharedGroupPurchaseStatus.OPEN, " +
            "com.cartmate.cartmate.shared.cart.SharedGroupPurchaseStatus.FROZEN) " +
            "and i.productId in :productIds")
    List<Long> findEditableIdsByProductIds(@Param("productIds") Collection<Long> productIds);

    // 회원 탈퇴 확인: 멤버로 참여 중인 모집 중(편집 가능·보류) · 공동 주문 생성 대기(CLOSED) 공동구매 수.
    // 주문이 만들어진 뒤(ORDER_CREATED)는 order 쪽 결제 중 공동 주문 확인이 맡는다
    @Query("select count(distinct g.id) from SharedGroupPurchase g join g.members m " +
            "where m.memberId = :memberId and g.status in (" +
            "com.cartmate.cartmate.shared.cart.SharedGroupPurchaseStatus.OPEN, " +
            "com.cartmate.cartmate.shared.cart.SharedGroupPurchaseStatus.FROZEN, " +
            "com.cartmate.cartmate.shared.cart.SharedGroupPurchaseStatus.CLOSED)")
    long countActiveOfMember(@Param("memberId") Long memberId);
}
