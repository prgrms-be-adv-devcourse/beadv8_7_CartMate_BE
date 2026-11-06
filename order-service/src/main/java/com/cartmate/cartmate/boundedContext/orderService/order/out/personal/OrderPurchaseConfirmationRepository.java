package com.cartmate.cartmate.boundedContext.orderService.order.out.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.OrderPurchaseConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface OrderPurchaseConfirmationRepository extends JpaRepository<OrderPurchaseConfirmation, Long> {

    // 주문당 한 번만 기록한다. 판매자가 여럿인 주문은 판매자마다 구매확정 이벤트가 오고, 개인 주문처럼 잠글 공동구매 사본이
    // 없으면 같은 주문의 기록이 동시에 들어올 수 있다. "있는지 보고 저장"은 둘 다 없다고 보고 PK 충돌로 실패하므로
    // DB가 한 번에 판단하게 한다. 먼저 넣은 확정 시각이 남고 나중 것은 아무것도 바꾸지 않는다.
    // 반환값은 드라이버 설정(found rows)에 따라 달라지므로 새로 넣었는지 판단에 쓰지 않는다
    @Modifying
    @Query(value = "INSERT INTO order_purchase_confirmation (order_id, confirmed_at) VALUES (:orderId, :confirmedAt) "
            + "ON DUPLICATE KEY UPDATE order_id = order_id", nativeQuery = true)
    int insertIfAbsent(@Param("orderId") Long orderId, @Param("confirmedAt") LocalDateTime confirmedAt);
}
