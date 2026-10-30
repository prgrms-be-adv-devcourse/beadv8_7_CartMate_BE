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
import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;

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

    // 판매 중지·숨김·삭제된 시각. 진행 중 거래 정리 대사(재처리) 대상을 찾는 데 쓴다.
    private LocalDateTime hiddenAt;

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
        this.hiddenAt = LocalDateTime.now();
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

    // ===== 관리자 숨김 =====
    public void validateSuspendable() {
        if (status != SalesPostSaleStatus.ON_SALE && status != SalesPostSaleStatus.PAUSED) {
            throw new ServiceException(SalesPostErrorCode.POST_SUSPEND_NOT_ALLOWED);
        }
    }

    public void validateResumable() {
        if (status != SalesPostSaleStatus.SUSPENDED) {
            throw new ServiceException(SalesPostErrorCode.POST_RESUME_NOT_ALLOWED);
        }
    }

    public void suspendByAdmin() {
        validateSuspendable();
        this.status = SalesPostSaleStatus.SUSPENDED;
        this.hiddenAt = LocalDateTime.now();
    }

    public void resumeByAdmin() {
        validateResumable();
        this.status = SalesPostSaleStatus.ON_SALE;
    }

    // ===== 판매자 내림 =====
    public void validatePausable() {
        if (status != SalesPostSaleStatus.ON_SALE) {
            throw new ServiceException(SalesPostErrorCode.POST_PAUSE_NOT_ALLOWED);
        }
    }

    public void validateResumableBySeller() {
        if (status != SalesPostSaleStatus.PAUSED) {
            throw new ServiceException(SalesPostErrorCode.POST_SELLER_RESUME_NOT_ALLOWED);
        }
    }

    public void pauseBySeller() {
        validatePausable();
        this.status = SalesPostSaleStatus.PAUSED;
        this.hiddenAt = LocalDateTime.now();
    }

    public void resumeBySeller() {
        validateResumableBySeller();
        this.status = SalesPostSaleStatus.ON_SALE;
    }
}