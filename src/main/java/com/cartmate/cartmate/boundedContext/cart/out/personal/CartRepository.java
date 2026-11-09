package com.cartmate.cartmate.boundedContext.cart.out.personal;

import com.cartmate.cartmate.boundedContext.cart.domain.personal.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long>, CartRepositoryCustom {

    Optional<Cart> findByMemberId(Long memberId);

    boolean existsByMemberId(Long memberId);

    // 회원 장바구니가 없을 때만 만든다. 같은 회원 요청이 동시에 와도 예외 없이 한쪽만 insert되고,
    // 다른 쪽은 먼저 넣은 트랜잭션이 끝날 때까지 기다렸다가 아무것도 바꾸지 않는다
    @Modifying
    @Query(value = "INSERT INTO cart (member_id, created_at, updated_at) VALUES (:memberId, :now, :now) "
            + "ON DUPLICATE KEY UPDATE member_id = member_id", nativeQuery = true)
    int insertIfAbsent(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
