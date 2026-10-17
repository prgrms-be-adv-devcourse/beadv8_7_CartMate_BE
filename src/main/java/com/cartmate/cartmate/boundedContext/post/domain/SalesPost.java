package com.cartmate.cartmate.boundedContext.post.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "POST_POST")
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
    private SalesPostSaleStatus saleStatus;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long viewCount;

    public SalesPost(Long sellerId, String title, String content, SalesPostSaleStatus saleStatus, Long viewCount) {
        this.sellerId = sellerId;
        this.title = title;
        this.content = content;
        this.saleStatus = saleStatus;
        this.viewCount = viewCount;
    }


    public boolean isVisible() {
        return saleStatus.isVisible();
    }

    public SalesPost delete() {
        this.saleStatus = SalesPostSaleStatus.DELETED;
        return this;
    }
}
