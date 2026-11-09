package com.cartmate.cartmate.boundedContext.productService.post.out;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostUpdateStatus;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectSalesPostRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface SalesPostQueryRepository {
    Page<SalesPost> selectSalesPostAll(SelectSalesPostRequest request, Pageable pageable);

    Long changeUpdateStatus(Long salesPostId, Collection<SalesPostUpdateStatus> from,
                            SalesPostUpdateStatus to, LocalDateTime now);

    Long startUpdate(Long salesPostId, Collection<SalesPostUpdateStatus> from, LocalDateTime now);

    Long startDelete(Long salesPostId, Collection<SalesPostUpdateStatus> editable);

    // 삭제 대사: DELETING인 판매글의 삭제 시작 시각(modifyDate)을 지금으로 다시 잡는다. DELETING이 아니면 0
    Long refreshDeleting(Long salesPostId, LocalDateTime now);

    Page<SalesPost> selectForAdmin(SalesPostSaleStatus status, String keyword, Pageable pageable);

    // 상태별 판매글 수를 한 번의 GROUP BY 조회로 (없는 상태는 결과에 없음)
    Map<SalesPostSaleStatus, Long> countByStatuses(Collection<SalesPostSaleStatus> statuses);
}
