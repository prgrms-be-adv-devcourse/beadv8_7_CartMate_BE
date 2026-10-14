package com.cartmate.cartmate.domain.cart.groupshared.repository;

import com.cartmate.cartmate.domain.cart.groupshared.entity.SharedGroupPurchase;
import org.springframework.data.jpa.repository.JpaRepository;


public interface SharedGroupPurchaseRepository extends JpaRepository<SharedGroupPurchase, Long>, SharedGroupPurchaseRepositoryCustom {

    boolean existsByInviteCode(String inviteCode);
}
