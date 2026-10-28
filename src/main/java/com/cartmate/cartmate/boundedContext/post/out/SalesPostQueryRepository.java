package com.cartmate.cartmate.boundedContext.post.out;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostUpdateStatus;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SalesPostQueryRepository {
    Page<SalesPost> selectSalesPostAll(SelectSalesPostRequest request, Pageable pageable);

    Long changeUpdateStatus(Long salesPostId, Collection<SalesPostUpdateStatus> from,
                            SalesPostUpdateStatus to, LocalDateTime now);

    Long startUpdate(Long salesPostId, Collection<SalesPostUpdateStatus> from, LocalDateTime now);

    Long startDelete(Long salesPostId, Collection<SalesPostUpdateStatus> editable);
}
