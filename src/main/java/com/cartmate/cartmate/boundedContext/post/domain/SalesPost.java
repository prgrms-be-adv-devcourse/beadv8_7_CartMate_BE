package com.cartmate.cartmate.boundedContext.post.domain;


import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostProduct;
import com.cartmate.cartmate.boundedContext.post.dto.SelectSalesPostResponse;
import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private SalesPostSaleStatus status;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long viewCount;

    @OneToMany(mappedBy = "salesPost", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval = true)
    @SQLRestriction("status in ('ON_SALE')")
    private List<SalesPostProduct> productList = new ArrayList<>();

    public SalesPost(Long sellerId, String title, String content, SalesPostSaleStatus status, Long viewCount) {
        this.sellerId = sellerId;
        this.title = title;
        this.content = content;
        this.status = status;
        this.viewCount = viewCount;
    }

    public SelectSalesPostResponse toSelectResultDto() {
        List<SelectSalesPostProduct> resultList = productList
                .stream()
                .map(SalesPostProduct::toSelectProductDto)
                .toList();
        return new SelectSalesPostResponse(
                getId(),
                sellerId,
                title,
                content,
                viewCount,
                status,
                resultList,
                getCreateDate(),
                getModifyDate()
        );
    }

    public SalesPost delete() {
        this.status = SalesPostSaleStatus.DELETED;
        return this;
    }
}
