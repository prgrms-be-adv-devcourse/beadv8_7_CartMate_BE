package com.cartmate.cartmate.boundedContext.delivery.out;

import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaimItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface DeliveryClaimItemRepository extends JpaRepository<DeliveryClaimItem, Long> {

    // 지정한 상태의 클레임에 이미 포함된 배송상품이 하나라도 있는지 확인한다.
    @Query("select count(ci) > 0 from DeliveryClaimItem ci "
            + "where ci.deliveryItem.id in :deliveryItemIds and ci.claim.status in :statuses")
    boolean existsByDeliveryItemIdsAndClaimStatuses(
            @Param("deliveryItemIds") Collection<Long> deliveryItemIds,
            @Param("statuses") Collection<ClaimStatus> statuses);
}
