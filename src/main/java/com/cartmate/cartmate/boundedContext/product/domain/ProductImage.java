package com.cartmate.cartmate.boundedContext.product.domain;


import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import com.cartmate.cartmate.shared.product.dto.SyncProductImage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
        name = "PRODUCT_PRODUCT_IMAGE",
        indexes = @Index(name = "idx_product_image_product", columnList = "product_id, image_seq")
)
@NoArgsConstructor
@Getter
public class ProductImage extends BaseIdAndTime {
    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long sellerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String s3Key;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private int imageSeq;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ProductStatus status;

    @Column(nullable = false)
    private boolean syncStatus;

    public ProductImage(Long sellerId, Product product, String s3Key, int imageSeq,
                        ProductStatus status, boolean syncStatus) {
        this.sellerId = sellerId;
        this.product = product;
        this.s3Key = s3Key;
        this.imageSeq = imageSeq;
        this.status = status;
        this.syncStatus = syncStatus;
    }

    public SyncProductImage toSyncProductImage() {
        return new SyncProductImage(
                getId(),
                getSellerId(),
                getS3Key(),
                getImageSeq(),
                getStatus(),
                getCreateDate(),
                getModifyDate()
        );
    }

    public ProductImage delete() {
        this.status = ProductStatus.DELETED;
        return this;
    }

    public void updateSyncStatus() {
        this.syncStatus = true;
    }

    public void changeSeq(int imageSeq) {
        this.imageSeq = imageSeq;
        this.syncStatus = false; // 동기화상태 초기화
    }

    // 복구: 삭제됐던 이미지도 다시 활성화하고 순서를 되돌림
    public void restore(int imageSeq) {
        this.status = ProductStatus.ACTIVE;
        changeSeq(imageSeq);
    }
}
