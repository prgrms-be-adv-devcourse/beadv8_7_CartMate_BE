package com.cartmate.cartmate.boundedContext.orderService.cart.out.personal;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.personal.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface CartRepository extends JpaRepository<Cart, Long>, CartRepositoryCustom {

    boolean existsByMemberId(Long memberId);

    @Modifying
    @Query(value = "INSERT INTO cart (member_id, created_at, updated_at) VALUES (:memberId, :now, :now) "
            + "ON DUPLICATE KEY UPDATE member_id = member_id", nativeQuery = true)
    int insertIfAbsent(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
