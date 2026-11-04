package com.cartmate.cartmate.boundedContext.cart.out.groupshared;

import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SharedGroupPurchaseRepositoryCustom {

    Optional<SharedGroupPurchase> findByIdForUpdate(Long id);

    Optional<SharedGroupPurchase> findByInviteCodeForUpdate(String inviteCode);

    Optional<SharedGroupPurchase> findWithDetailsById(Long id);

    List<SharedGroupPurchase> findAllByParticipant(Long memberId);

    List<Long> findIdsToCloseByDeadline(LocalDateTime now);

    List<Long> findIdsToReleaseFrozen(LocalDateTime now);

    // 공동 주문 생성 대기(CLOSED)가 기준 시각 이전에 마감된 것
    List<Long> findIdsAwaitingOrderClosedBefore(LocalDateTime threshold);

    Map<Long, String> findTitlesByIds(Collection<Long> ids);
}
