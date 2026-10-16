package com.cartmate.cartmate.boundedContext.cart.out;

import com.cartmate.cartmate.boundedContext.cart.domain.SharedGroupPurchase;
import org.springframework.data.jpa.repository.JpaRepository;


public interface SharedGroupPurchaseRepository extends JpaRepository<SharedGroupPurchase, Long>, SharedGroupPurchaseRepositoryCustom {

    boolean existsByInviteCode(String inviteCode);
}
