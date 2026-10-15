package com.cartmate.cartmate.domain.cart.groupindividual.repository;

import com.cartmate.cartmate.domain.cart.groupindividual.entity.IndividualGroupPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface IndividualGroupPurchaseRepository
        extends JpaRepository<IndividualGroupPurchase, Long>, IndividualGroupPurchaseRepositoryCustom {

    boolean existsByInviteCode(String inviteCode);


    List<IndividualGroupPurchase> findAllByCreatorMemberIdOrderByIdDesc(Long creatorMemberId);
}
