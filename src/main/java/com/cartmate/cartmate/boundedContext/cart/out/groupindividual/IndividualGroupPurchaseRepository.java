package com.cartmate.cartmate.boundedContext.cart.out.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;


public interface IndividualGroupPurchaseRepository
        extends JpaRepository<IndividualGroupPurchase, Long>, IndividualGroupPurchaseRepositoryCustom {

    boolean existsByInviteCode(String inviteCode);


    List<IndividualGroupPurchase> findAllByCreatorMemberIdOrderByIdDesc(Long creatorMemberId);

    // 판매글 판매 중지 대상: 해당 상품이 들어 있는 모집 중 공동구매
    @Query("select distinct g.id from IndividualGroupPurchase g join g.products p " +
            "where g.status = com.cartmate.cartmate.boundedContext.cart.domain.groupindividual.IndividualGroupPurchaseStatus.OPEN " +
            "and p.productId in :productIds")
    List<Long> findOpenIdsByProductIds(@Param("productIds") Collection<Long> productIds);
}
