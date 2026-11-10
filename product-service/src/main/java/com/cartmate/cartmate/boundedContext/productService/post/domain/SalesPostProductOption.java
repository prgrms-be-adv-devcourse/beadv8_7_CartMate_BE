package com.cartmate.cartmate.boundedContext.productService.post.domain;

import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectSalesPostProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SyncProductOption;
import com.cartmate.cartmate.global.jpa.entity.BaseManualIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Table(name = "POST_PRODUCT_OPTION")
@NoArgsConstructor
@Getter
public class SalesPostProductOption extends BaseManualIdAndTime {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private SalesPostProduct salesPostProduct;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long productTypeId;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long productOptionId;

    // product 옵션과 같은 상태를 복제한다(논리 삭제). 컬럼이 생기기 전에 있던 옵션은 ACTIVE로 채운다(ddl-auto: update)
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVE'")
    private SalesPostProductStatus status;

    public SalesPostProductOption(Long id, SalesPostProduct salesPostProduct, Long productTypeId, Long productOptionId,
                                  SalesPostProductStatus status, LocalDateTime createDate, LocalDateTime modifyDate) {
        super(id, createDate, modifyDate);
        this.salesPostProduct = salesPostProduct;
        this.productTypeId = productTypeId;
        this.productOptionId = productOptionId;
        this.status = status;
    }

    public static SalesPostProductOption from(SyncProductOption option, SalesPostProduct salesPostProduct) {
        return new SalesPostProductOption(
                option.getId(),
                salesPostProduct,
                option.getProductTypeId(),
                option.getProductOptionId(),
                // 상태 없이 온 옛 형식의 동기화 데이터는 ACTIVE로 본다
                option.getStatus() == null ? SalesPostProductStatus.ACTIVE : option.getStatus(),
                option.getCreateDate(),
                option.getModifyDate()
        );
    }

    public SalesPostProductOption delete() {
        this.status = SalesPostProductStatus.DELETED;
        return this;
    }

}
