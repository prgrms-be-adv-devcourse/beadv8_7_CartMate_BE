package com.cartmate.cartmate.boundedContext.delivery.out;

import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaimItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DeliveryClaimItemRepository extends JpaRepository<DeliveryClaimItem, Long> {

    // 지정한 상태의 클레임에 이미 포함된 배송상품이 하나라도 있는지 확인한다.
    @Query("select count(ci) > 0 from DeliveryClaimItem ci "
            + "where ci.deliveryItem.id in :deliveryItemIds and ci.claim.status in :statuses")
    boolean existsByDeliveryItemIdsAndClaimStatuses(
            @Param("deliveryItemIds") Collection<Long> deliveryItemIds,
            @Param("statuses") Collection<ClaimStatus> statuses);

    // 여러 클레임의 상품을 배송상품과 함께 한 번에 조회한다. 상품마다 배송상품을 따로 조회하는 N+1을 막는다.
    @Query("select ci from DeliveryClaimItem ci join fetch ci.deliveryItem "
            + "where ci.claim.id in :claimIds order by ci.id")
    List<DeliveryClaimItem> findAllWithDeliveryItemByClaimIds(@Param("claimIds") Collection<Long> claimIds);
}
