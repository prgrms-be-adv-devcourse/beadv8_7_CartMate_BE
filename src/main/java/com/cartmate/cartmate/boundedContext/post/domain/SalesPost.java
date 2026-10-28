package com.cartmate.cartmate.boundedContext.post.domain;


import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostProduct;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostResponse;
import com.cartmate.cartmate.boundedContext.post.dto.UpdateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.product.domain.ProductUpdateStatus;
import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private SalesPostSaleStatus status;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long viewCount;

    @OneToMany(mappedBy = "salesPost", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval = true)
    @SQLRestriction("status in ('ON_SALE')")
    private List<SalesPostProduct> productList = new ArrayList<>();

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private SalesPostUpdateStatus updateStatus;

    @Column(nullable = false)
    private LocalDateTime updateStatusChangedAt;

    public SalesPost(Long sellerId, String title, String content, SalesPostSaleStatus status, Long viewCount,
                     SalesPostUpdateStatus updateStatus, LocalDateTime updateStatusChangedAt) {
        this.sellerId = sellerId;
        this.title = title;
        this.content = content;
        this.status = status;
        this.viewCount = viewCount;
        this.updateStatus = updateStatus;
        this.updateStatusChangedAt = updateStatusChangedAt;
    }

    public SalesPost deleteComplete() {
        this.status = SalesPostSaleStatus.DELETED;
        return this;
    }

    public SalesPost saveComplete() {
        this.status = SalesPostSaleStatus.ON_SALE;
        return this;
    }

    public SalesPost updateContent(String title, String content) {
        this.title = title;
        this.content = content;
        return this;
    }
}