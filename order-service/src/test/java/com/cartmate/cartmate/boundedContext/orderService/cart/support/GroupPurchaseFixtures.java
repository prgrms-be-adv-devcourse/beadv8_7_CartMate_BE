package com.cartmate.cartmate.boundedContext.orderService.cart.support;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchase;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupshared.SharedGroupPurchase;

import java.time.LocalDateTime;
import java.util.List;

/**
 * cart 테스트가 함께 쓰는 공동구매 픽스처. 시각은 모두 인자로 받는다.
 */
public final class GroupPurchaseFixtures {

    /** 초대 코드를 따로 주지 않을 때 쓰는 값. DB에 여러 건을 저장하는 테스트는 초대 코드를 직접 넘긴다 */
    private static final String INVITE_CODE = "abcdefghijkl";
    private static final String DEMAND_INVITE_CODE = "invitecode01";

    /** 수요 공동구매의 기본 페이백 조건: 10명 이상이면 1,000원 */
    private static final int PAYBACK_MIN_BUYERS = 10;
    private static final long PAYBACK_AMOUNT = 1_000L;

    private GroupPurchaseFixtures() {
    }

    /** 회원이 만든 개인 공동구매 */
    public static IndividualGroupPurchase memberPurchase(Long creatorMemberId, List<Long> productIds,
                                                         LocalDateTime createdAt) {
        return memberPurchase(creatorMemberId, INVITE_CODE, productIds, createdAt);
    }

    public static IndividualGroupPurchase memberPurchase(Long creatorMemberId, String inviteCode, List<Long> productIds,
                                                         LocalDateTime createdAt) {
        return IndividualGroupPurchase.create("OO FC 2026 유니폼", creatorMemberId, inviteCode, productIds, createdAt);
    }

    /** 어드민이 수요로 연 개인 공동구매. deadlineAt이 null이면 기본 판매 기한을 쓴다 */
    public static IndividualGroupPurchase demandPurchase(Long adminMemberId, Long productId, LocalDateTime deadlineAt,
                                                         LocalDateTime openedAt) {
        return demandPurchase(adminMemberId, DEMAND_INVITE_CODE, productId, deadlineAt, openedAt);
    }

    public static IndividualGroupPurchase demandPurchase(Long adminMemberId, String inviteCode, Long productId,
                                                         LocalDateTime deadlineAt, LocalDateTime openedAt) {
        return IndividualGroupPurchase.openFromDemand("수요 공동구매", adminMemberId, inviteCode, productId, deadlineAt,
                PAYBACK_MIN_BUYERS, PAYBACK_AMOUNT, openedAt);
    }

    /** 방장과 참여자 한 명이 들어와 있고 아직 상품은 없는 공유 공동구매(최대 5명) */
    public static SharedGroupPurchase sharedPurchase(Long creatorMemberId, Long participantMemberId,
                                                     LocalDateTime createdAt) {
        SharedGroupPurchase purchase = SharedGroupPurchase.create("FC 훈련용품", creatorMemberId, 5, INVITE_CODE, createdAt);
        purchase.join(participantMemberId, createdAt);
        return purchase;
    }
}
