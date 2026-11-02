package com.cartmate.cartmate.boundedContext.post.out;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostSaleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SalesPostRepository extends JpaRepository<SalesPost, Long>, SalesPostQueryRepository {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from SalesPost p where p.id = :id")
    Optional<SalesPost> findLatestById(Long id);
    long countByStatus(SalesPostSaleStatus status);

    // 판매글 숨김 대사: 최근 내려간 판매글
    List<SalesPost> findByStatusInAndHiddenAtAfter(Collection<SalesPostSaleStatus> statuses, LocalDateTime after);
}
