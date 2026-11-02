package com.cartmate.cartmate.shared.cart;

import com.cartmate.cartmate.shared.cart.dto.CartOrderItem;
import com.cartmate.cartmate.shared.cart.dto.IndividualGroupPurchaseOrderable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// cart가 order에 내주는 조회 창구. order는 cart의 Facade 대신 이것만 의존한다 (cart ↔ order 순환 방지, 물리 분리 시 API로 교체)
// 조회할 수 없으면 ServiceException(CartErrorCode)을 그대로 던진다. 물리 분리 뒤엔 order 쪽 클라이언트가 응답 코드를 같은 예외로 되돌린다
public interface CartOrderQuery {

    // 회원 장바구니에서 고른 항목. 장바구니가 없거나 다른 회원 항목이면 예외
    List<CartOrderItem> getCartItemsForOrder(Long memberId, List<Long> cartItemIds);

    // 개인물품 공동구매에 지금 이 상품들로 주문할 수 있는지 확인하고 판매 마감 시각을 돌려준다. 안 되면 예외
    IndividualGroupPurchaseOrderable getIndividualGroupPurchaseOrderable(Long individualGroupPurchaseId,
                                                                        List<Long> productIds, LocalDateTime now);

    // 공동구매 ID별 제목. 없는 ID는 빠진다
    Map<Long, String> findSharedGroupPurchaseTitles(Collection<Long> sharedGroupPurchaseIds);
}
