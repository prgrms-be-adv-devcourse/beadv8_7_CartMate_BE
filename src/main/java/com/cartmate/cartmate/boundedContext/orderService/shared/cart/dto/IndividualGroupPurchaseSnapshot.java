package com.cartmate.cartmate.boundedContext.orderService.shared.cart.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 개인물품 공동구매의 지금 상태. order가 주문 가능 여부를 스스로 판단하는 데 필요한 것만 담는다.
 * 이벤트 사본이 아직 없을 때 order가 cart에 동기로 물어 받는다(잠그지 않음).
 *
 * @param closed     DB 상태가 CLOSED인가. 회원 공동구매는 마감 시각이 지나도 false일 수 있다 → deadlineAt과 함께 판단
 * @param closedAt   닫힌 시각 (열려 있으면 null)
 * @param productIds 지금 파는 상품 ID
 * @param revision   공동구매가 바뀔 때마다 1씩 오르는 번호. 늦게 온 상태가 새 상태를 덮지 않게 한다
 */
public record IndividualGroupPurchaseSnapshot(
        Long individualGroupPurchaseId,
        boolean closed,
        LocalDateTime deadlineAt,
        LocalDateTime closedAt,
        List<Long> productIds,
        long revision
) {
}
