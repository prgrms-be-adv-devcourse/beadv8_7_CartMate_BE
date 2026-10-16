package com.cartmate.cartmate.boundedContext.cart.out;

import com.cartmate.cartmate.boundedContext.cart.domain.IndividualGroupPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface IndividualGroupPurchaseRepository
        extends JpaRepository<IndividualGroupPurchase, Long>, IndividualGroupPurchaseRepositoryCustom {

    boolean existsByInviteCode(String inviteCode);


    List<IndividualGroupPurchase> findAllByCreatorMemberIdOrderByIdDesc(Long creatorMemberId);
}
