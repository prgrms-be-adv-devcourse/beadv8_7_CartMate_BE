package com.cartmate.cartmate.boundedContext.orderService.cart.out.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SharedGroupPurchaseRepository extends JpaRepository<SharedGroupPurchase, Long>, SharedGroupPurchaseRepositoryCustom {

    boolean existsByInviteCode(String inviteCode);

    @Query("select distinct g.id from SharedGroupPurchase g join g.items i " +
            "where g.status in (com.cartmate.cartmate.shared.cart.SharedGroupPurchaseStatus.OPEN, " +
            "com.cartmate.cartmate.shared.cart.SharedGroupPurchaseStatus.FROZEN) " +
            "and i.productId in :productIds")
    List<Long> findEditableIdsByProductIds(@Param("productIds") Collection<Long> productIds);

    @Query("select count(distinct g.id) from SharedGroupPurchase g join g.members m " +
            "where m.memberId = :memberId and g.status in (" +
            "com.cartmate.cartmate.shared.cart.SharedGroupPurchaseStatus.OPEN, " +
            "com.cartmate.cartmate.shared.cart.SharedGroupPurchaseStatus.FROZEN, " +
            "com.cartmate.cartmate.shared.cart.SharedGroupPurchaseStatus.CLOSED)")
    long countActiveOfMember(@Param("memberId") Long memberId);
}
