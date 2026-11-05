package com.cartmate.cartmate.boundedContext.orderService.shared.cart;

import com.cartmate.cartmate.boundedContext.orderService.shared.cart.dto.CartOrderItem;
import com.cartmate.cartmate.boundedContext.orderService.shared.cart.dto.IndividualGroupPurchaseSnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// cart가 order에 내주는 조회 창구. order는 cart의 Facade 대신 이것만 의존한다 (cart ↔ order 순환 방지, 물리 분리 시 API로 교체)
// 조회할 수 없으면 ServiceException(CartErrorCode)을 그대로 던진다. 물리 분리 뒤엔 order 쪽 클라이언트가 응답 코드를 같은 예외로 되돌린다
public interface CartOrderQuery {

    // 회원 장바구니에서 고른 항목. 장바구니가 없거나 다른 회원 항목이면 예외
    List<CartOrderItem> getCartItemsForOrder(Long memberId, List<Long> cartItemIds);

    // 개인물품 공동구매의 지금 상태(잠그지 않음). order는 보통 이벤트 사본으로 판단하고, 사본이 아직 없을 때만 부른다
    Optional<IndividualGroupPurchaseSnapshot> findIndividualGroupPurchaseSnapshot(Long individualGroupPurchaseId);

    // 공동구매 ID별 제목. 없는 ID는 빠진다
    Map<Long, String> findSharedGroupPurchaseTitles(Collection<Long> sharedGroupPurchaseIds);
}
