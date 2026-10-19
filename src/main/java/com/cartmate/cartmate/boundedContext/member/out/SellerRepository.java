package com.cartmate.cartmate.boundedContext.member.out;

import com.cartmate.cartmate.boundedContext.member.domain.Seller;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SellerRepository extends JpaRepository<Seller, Long> {

    Optional<Seller> findByMemberId(Long memberId);

    boolean existsByMemberId(Long memberId);

    boolean existsByBusinessNumber(String businessNumber);
}