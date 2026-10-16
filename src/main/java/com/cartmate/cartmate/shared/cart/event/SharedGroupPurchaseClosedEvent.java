package com.cartmate.cartmate.shared.cart.event;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 공동구매-공동물품 마감 이벤트 (cart → order 공유 계약)
 *
 * shared 패키지는 여러 모듈이 함께 보는 "계약"만 둔다. 그래서 이 record는 값(ID, 숫자, 시각)만 담고,
 * 특정 모듈의 내부 클래스(cart의 엔티티 등)를 import하지 않는다.
 * 이벤트를 만드는 코드(엔티티 → 이벤트 변환)는 보내는 쪽인 cart의 서비스에 있다.
 * shared가 cart 내부를 알게 되면, 이 이벤트를 받는 order도 간접적으로 cart 내부에 묶이게 되기 때문이다.
 */
public record SharedGroupPurchaseClosedEvent(
        Long sharedGroupPurchaseId,
        Long creatorMemberId,
        List<Long> participantMemberIds,
        List<Item> items,
        LocalDateTime closedAt
) {

    public record Item(
            Long productId,
            Long inventoryId,
            int quantity
    ) {
    }
}
