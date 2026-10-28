package com.cartmate.cartmate.shared.delivery.event;

import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;

/**
 * 판매글 숨김으로 발송 전 배송(또는 배송 항목)을 취소했을 때 delivery가 발행한다.
 * 배송 하나(판매자 하나)마다 한 번.
 *
 * <p>delivery는 배송 취소와 같은 트랜잭션에서 발행한다 ({@code @EventListener}로 받는 쪽도 같은 트랜잭션).
 * 환불이 실패하면 배송 취소도 롤백되어 "배송은 취소됐는데 돈은 안 돌아간" 상태가 생기지 않는다.
 * (클레임 승인 → 환불과 같은 방식)
 *
 * <ul>
 *   <li>INDIVIDUAL_GROUP: payment가 refundAmount만큼 부분 환불</li>
 *   <li>SHARED_GROUP: payment는 무시. order가 공동 주문을 취소하고 기존 취소 이벤트로 전원 환불</li>
 * </ul>
 *
 * @param refundAmount 취소한 상품의 단가 × 수량 합계
 */
public record DeliveryCanceledByPostHiddenEvent(
        Long postId,
        Long deliveryId,
        Long orderId,
        PurchaseType purchaseType,
        Long buyerMemberId,
        Long sellerId,
        Long refundAmount
) {
}