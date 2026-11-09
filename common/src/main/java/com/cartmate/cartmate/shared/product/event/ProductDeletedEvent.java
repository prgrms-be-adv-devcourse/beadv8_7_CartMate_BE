package com.cartmate.cartmate.shared.product.event;

import com.cartmate.cartmate.global.event.DomainEvent;

/**
 * 판매자가 상품 하나를 삭제했을 때 product가 발행한다(판매글은 그대로).
 *
 * <p>post는 그 상품의 복제본을 지운다. 같은 이벤트가 두 번 와도 결과가 같아야 한다(멱등).
 * Kafka 토픽: cartmate.product.product-deleted
 *
 * @param productId   삭제된 상품 ID
 * @param salesPostId 상품이 속한 판매글 ID
 */
public record ProductDeletedEvent(
        Long productId,
        Long salesPostId
) implements DomainEvent {

    // 판매글 단위 이벤트(판매글 삭제 등)와 같은 키(판매글 ID)를 써서 같은 판매글의 이벤트끼리 순서를 지킨다
    @Override
    public String aggregateType() {
        return "SalesPost";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(salesPostId);
    }
}
