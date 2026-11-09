package com.cartmate.cartmate.shared.product.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.util.List;

/**
 * 판매글 삭제({@code SalesPostDeletedEvent})를 받아 상품 삭제를 마쳤을 때 product가 발행한다.
 *
 * <p>post는 상품 복제본을 지우고 판매글 삭제를 완료(DELETED)한다.
 * 같은 이벤트가 두 번 와도 결과가 같아야 한다(멱등). Kafka 토픽: cartmate.product.products-deleted-by-post
 *
 * @param postId     삭제된 판매글 ID
 * @param productIds 삭제된 상품 ID 전체
 */
public record ProductsDeletedByPostEvent(
        Long postId,
        List<Long> productIds
) implements DomainEvent {

    // 판매글 삭제 이벤트와 같은 키(판매글 ID)를 써서 같은 판매글의 이벤트끼리 순서를 지킨다
    @Override
    public String aggregateType() {
        return "SalesPost";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(postId);
    }
}
