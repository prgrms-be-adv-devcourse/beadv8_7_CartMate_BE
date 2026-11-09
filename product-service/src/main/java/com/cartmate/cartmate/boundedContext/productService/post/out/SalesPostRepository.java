package com.cartmate.cartmate.boundedContext.productService.post.out;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostUpdateStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SalesPostRepository extends JpaRepository<SalesPost, Long>, SalesPostQueryRepository {
    // 판매글 숨김 대사: 최근 내려간 판매글
    List<SalesPost> findByStatusInAndHiddenAtAfter(Collection<SalesPostSaleStatus> statuses, LocalDateTime after);

    // 삭제 대사: 삭제 시작(modifyDate) 후 오래 지난 DELETING 판매글
    List<SalesPost> findByStatusAndModifyDateBeforeOrderByIdAsc(SalesPostSaleStatus status, LocalDateTime before, Limit limit);

    // 수정 복구 대사: 수정 상태(PENDING · RECOVERING)가 바뀐 지 오래된 판매글. afterId 다음부터 읽어,
    // 한 번 실행 안에서 이미 시도한(실패해 상태가 그대로인) 판매글을 다시 읽지 않고 다음으로 넘어간다
    List<SalesPost> findByUpdateStatusAndUpdateStatusChangedAtBeforeAndIdGreaterThanOrderByIdAsc(
            SalesPostUpdateStatus updateStatus, LocalDateTime before, Long afterId, Limit limit);

    // 회원 탈퇴 신청: 판매자의 특정 상태 판매글 ID
    @Query("select p.id from SalesPost p where p.sellerId = :sellerId and p.status = :status order by p.id")
    List<Long> findIdsBySellerIdAndStatus(@Param("sellerId") Long sellerId, @Param("status") SalesPostSaleStatus status);
}
