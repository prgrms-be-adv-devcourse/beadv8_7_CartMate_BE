package com.cartmate.cartmate.boundedContext.post.domain;

import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import com.cartmate.cartmate.shared.post.dto.SalesPostRollbackRequest;
import com.cartmate.cartmate.shared.post.event.PostDeletedEvent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "POST_SALES_POST")
@NoArgsConstructor
@Getter
public class SalesPost extends BaseIdAndTime {
    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long sellerId;

    @Column(nullable = false)
    private String title;

    private String content;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private SalesPostSaleStatus status;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long viewCount;

    public SalesPost(Long sellerId, String title, String content, SalesPostSaleStatus status, Long viewCount) {
        this.sellerId = sellerId;
        this.title = title;
        this.content = content;
        this.status = status;
        this.viewCount = viewCount;
    }

    public SalesPostRollbackRequest toRollbackDto() {
        return new SalesPostRollbackRequest(this.getId());
    }


    public boolean isVisible() {
        return status.isVisible();
    }

    public SalesPost delete() {
        this.status = SalesPostSaleStatus.DELETED;
        return this;
    }
}
