package com.cartmate.cartmate.shared.post.event;

import com.cartmate.cartmate.global.event.DomainEvent;

/**
 * 판매글이 삭제되었을 때 post가 발행한다. 판매글은 DELETING 상태로 바뀐 뒤이고(사용자에게는 이미 삭제됨), 삭제 완료(DELETED)는 상품 삭제 뒤에 한다.
 *
 * <p>product는 판매글에 속한 상품을 삭제하고 {@code ProductsDeletedByPostEvent}를 발행한다.
 * 같은 이벤트가 두 번 와도 결과가 같아야 한다(멱등). Kafka 토픽: cartmate.post.sales-post-deleted
 *
 * @param postId   삭제할 판매글 ID
 * @param sellerId 판매글 주인 (판매자 회원 ID)
 */
public record SalesPostDeletedEvent(
        Long postId,
        Long sellerId
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "SalesPost";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(postId);
    }
}
