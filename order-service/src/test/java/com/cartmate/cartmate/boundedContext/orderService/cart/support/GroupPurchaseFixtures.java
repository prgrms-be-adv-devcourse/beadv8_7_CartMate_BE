package com.cartmate.cartmate.boundedContext.orderService.cart.support;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchase;

import java.time.LocalDateTime;
import java.util.List;

public final class GroupPurchaseFixtures {

    private static final String INVITE_CODE = "abcdefghijkl";
    private static final String DEMAND_INVITE_CODE = "invitecode01";

    private GroupPurchaseFixtures() {
    }

    public static IndividualGroupPurchase memberPurchase(Long creatorMemberId, List<Long> productIds,
                                                         LocalDateTime createdAt) {
        return memberPurchase(creatorMemberId, INVITE_CODE, productIds, createdAt);
    }

    public static IndividualGroupPurchase memberPurchase(Long creatorMemberId, String inviteCode, List<Long> productIds,
                                                         LocalDateTime createdAt) {
        return IndividualGroupPurchase.create("OO FC 2026 유니폼", creatorMemberId, inviteCode, productIds, createdAt);
    }

    public static IndividualGroupPurchase demandPurchase(Long adminMemberId, Long productId, LocalDateTime deadlineAt,
                                                         LocalDateTime openedAt) {
        return demandPurchase(adminMemberId, DEMAND_INVITE_CODE, productId, deadlineAt, openedAt);
    }

    public static IndividualGroupPurchase demandPurchase(Long adminMemberId, String inviteCode, Long productId,
                                                         LocalDateTime deadlineAt, LocalDateTime openedAt) {
        return IndividualGroupPurchase.openFromDemand("수요 공동구매", adminMemberId, inviteCode, productId, deadlineAt,
                openedAt);
    }

    public static SharedGroupPurchase sharedPurchase(Long creatorMemberId, Long participantMemberId,
                                                     LocalDateTime createdAt) {
        return sharedPurchase(creatorMemberId, participantMemberId, INVITE_CODE, createdAt);
    }

    public static SharedGroupPurchase sharedPurchase(Long creatorMemberId, Long participantMemberId, String inviteCode,
                                                     LocalDateTime createdAt) {
        SharedGroupPurchase purchase = SharedGroupPurchase.create("FC 훈련용품", creatorMemberId, 5, inviteCode, createdAt);
        purchase.join(participantMemberId, createdAt);
        return purchase;
    }
}
