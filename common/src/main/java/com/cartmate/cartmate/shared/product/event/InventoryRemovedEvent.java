package com.cartmate.cartmate.shared.product.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.util.List;

/**
 * 판매글 수정에서 빠진 재고(판매 중지된 상품옵션 조합)를 알린다. 수정이 끝까지 성공했을 때만 post가 발행한다
 * (수정이 실패해 스냅샷으로 복구되면 재고가 같은 ID로 되살아나므로 발행하지 않는다).
 *
 * <p>받는 쪽(order: 그 재고를 담은 공동구매 · 결제 대기 주문 정리)은 장바구니 · 주문이 재고 ID로 바뀔 때 붙인다.
 * 같은 이벤트가 두 번 와도 결과가 같아야 한다(멱등). Kafka 토픽: cartmate.product.inventory-removed
 *
 * @param postId       판매글 ID
 * @param sellerId     판매자 ID
 * @param productId    재고가 빠진 상품 ID
 * @param inventoryIds 빠진 재고 ID (행은 남고 상태만 DELETED)
 */
public record InventoryRemovedEvent(
        Long postId,
        Long sellerId,
        Long productId,
        List<Long> inventoryIds
) implements DomainEvent {

    // 판매글 단위 이벤트(판매글 삭제 등)와 같은 키(판매글 ID)를 써서 같은 판매글의 이벤트끼리 순서를 지킨다
    @Override
    public String aggregateType() {
        return "SalesPost";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(postId);
    }
}
