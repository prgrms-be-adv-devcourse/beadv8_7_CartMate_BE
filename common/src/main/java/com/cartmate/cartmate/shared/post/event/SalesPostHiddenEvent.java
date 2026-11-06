package com.cartmate.cartmate.shared.post.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 판매글이 판매 중지·숨김·삭제되어 진행 중 거래를 정리해야 할 때 발행한다.
 *
 * <p>판매글 상태 변경이 커밋된 뒤에 받는다. 받는 쪽은
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} + {@code @Transactional(propagation = REQUIRES_NEW)}로,
 * 한 모듈의 처리가 실패해도 숨김 자체나 다른 모듈 처리가 롤백되지 않게 한다.
 * 같은 이벤트가 두 번 와도 결과가 같아야 한다(멱등). Kafka 전환 시 이 클래스가 메시지 스키마가 된다.
 *
 * <ul>
 *   <li>cart: 모집 중 공동구매에서 productIds 상품 제외, 남은 상품이 없으면 공동구매 취소</li>
 *   <li>order: 결제 대기 주문 취소, 결제 중 공동 주문 취소(→ 기존 취소 이벤트로 낸 사람 환불)</li>
 *   <li>delivery: 발송 전 배송 항목 취소 → {@code DeliveryCanceledByPostHiddenEvent} 발행</li>
 * </ul>
 *
 * @param productIds 판매글에 속한 상품 ID 전체 (장바구니·주문의 inventoryId/productId와 같은 값)
 * @param actorId    처리한 사람 (판매자 회원 ID 또는 관리자 ID)
 * @param reason     관리자 숨김 사유 등. 판매자 내림·삭제는 null 가능
 */
public record SalesPostHiddenEvent(
        Long postId,
        Long sellerId,
        List<Long> productIds,
        SalesPostHideType hideType,
        Long actorId,
        String reason,
        LocalDateTime hiddenAt
) {
}