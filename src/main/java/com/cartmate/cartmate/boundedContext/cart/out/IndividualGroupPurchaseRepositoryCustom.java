package com.cartmate.cartmate.boundedContext.cart.out;

import com.cartmate.cartmate.boundedContext.cart.domain.IndividualGroupPurchase;

import java.util.Optional;


public interface IndividualGroupPurchaseRepositoryCustom {

    Optional<IndividualGroupPurchase> findByIdForUpdate(Long id);

    Optional<IndividualGroupPurchase> findWithProductsByInviteCode(String inviteCode);

    Optional<IndividualGroupPurchase> findWithProductsById(Long id);
}
