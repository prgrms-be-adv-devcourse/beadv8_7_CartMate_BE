package com.cartmate.cartmate.boundedContext.order.out.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SharedGroupOrderRepository extends JpaRepository<SharedGroupOrder, Long>, SharedGroupOrderRepositoryCustom {

    boolean existsBySharedGroupPurchaseId(Long sharedGroupPurchaseId);

    // 판매글 판매 중지 대상: 해당 상품이 들어 있는, 특정 상태들의 공동 주문
    @Query("select distinct o.id from SharedGroupOrder o join o.items i " +
            "where o.status in :statuses and i.productId in :productIds order by o.id")
    List<Long> findIdsByStatusInAndProductIds(@Param("statuses") Collection<SharedGroupOrderStatus> statuses,
                                              @Param("productIds") Collection<Long> productIds);
}
