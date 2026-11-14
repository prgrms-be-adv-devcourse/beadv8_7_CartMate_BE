package com.cartmate.cartmate.boundedContext.orderService.cart.out.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface IndividualGroupPurchaseRepository
        extends JpaRepository<IndividualGroupPurchase, Long>, IndividualGroupPurchaseRepositoryCustom {

    boolean existsByInviteCode(String inviteCode);

    List<IndividualGroupPurchase> findAllByCreatorMemberIdOrderByIdDesc(Long creatorMemberId);

    @Query("select distinct g.id from IndividualGroupPurchase g join g.products p " +
            "where g.status = com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseStatus.OPEN " +
            "and p.productId in :productIds")
    List<Long> findOpenIdsByProductIds(@Param("productIds") Collection<Long> productIds);

    long countByCreatorMemberIdAndStatusAndDeadlineAtAfter(Long creatorMemberId, IndividualGroupPurchaseStatus status,
                                                           LocalDateTime now);
}
