package com.cartmate.cartmate.boundedContext.post.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalesPostRecoveryRepository extends JpaRepository<SalesPostRecovery, Long> {
    void deleteBySalesPostId(Long postId);

    Optional<SalesPostRecovery> findBySalesPostId(Long postId);
}
