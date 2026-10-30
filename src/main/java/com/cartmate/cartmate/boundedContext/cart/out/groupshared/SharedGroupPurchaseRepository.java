package com.cartmate.cartmate.boundedContext.cart.out.groupshared;

import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;


public interface SharedGroupPurchaseRepository extends JpaRepository<SharedGroupPurchase, Long>, SharedGroupPurchaseRepositoryCustom {

    boolean existsByInviteCode(String inviteCode);

    // 판매글 판매 중지 대상: 해당 상품이 담긴 모집 중(편집 가능·보류) 공동구매
    @Query("select distinct g.id from SharedGroupPurchase g join g.items i " +
            "where g.status in (com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchaseStatus.OPEN, " +
            "com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchaseStatus.FROZEN) " +
            "and i.productId in :productIds")
    List<Long> findEditableIdsByProductIds(@Param("productIds") Collection<Long> productIds);
}
