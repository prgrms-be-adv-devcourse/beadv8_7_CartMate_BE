package com.cartmate.cartmate.domain.cart.groupindividual.repository;

import com.cartmate.cartmate.domain.cart.groupindividual.entity.IndividualGroupPurchase;

import java.util.Optional;


public interface IndividualGroupPurchaseRepositoryCustom {

    Optional<IndividualGroupPurchase> findByIdForUpdate(Long id);

    Optional<IndividualGroupPurchase> findWithProductsByInviteCode(String inviteCode);

    Optional<IndividualGroupPurchase> findWithProductsById(Long id);
}
