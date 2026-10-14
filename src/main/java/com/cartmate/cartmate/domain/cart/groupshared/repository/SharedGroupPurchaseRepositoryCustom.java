package com.cartmate.cartmate.domain.cart.groupshared.repository;

import com.cartmate.cartmate.domain.cart.groupshared.entity.SharedGroupPurchase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SharedGroupPurchaseRepositoryCustom {

    Optional<SharedGroupPurchase> findByIdForUpdate(Long id);

    Optional<SharedGroupPurchase> findByInviteCodeForUpdate(String inviteCode);

    Optional<SharedGroupPurchase> findWithDetailsById(Long id);

    List<SharedGroupPurchase> findAllByParticipant(Long memberId);

    List<Long> findIdsToCloseByDeadline(LocalDateTime now);

    List<Long> findIdsToReleaseFrozen(LocalDateTime now);
}
