package com.cartmate.cartmate.boundedContext.post.domain;

import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.product.dto.UploadedImage;
import com.cartmate.cartmate.global.jpa.entity.BaseManualIdAndTime;
import com.cartmate.cartmate.shared.product.dto.RollbackProductRequest;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "POST_PRODUCT")
@NoArgsConstructor
@Getter
public class SalesPostProduct extends BaseManualIdAndTime {
    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long sellerId;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long salesPostId;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long categoryId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, columnDefinition = "BIGINT")
    private Long basePrice;

    @Column(nullable = false)
    private ProductStatus status;

    @OneToMany(mappedBy = "product", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval = true)
    private List<SalesPostProductImage> productImageList = new ArrayList<SalesPostProductImage>();


    public SalesPostProduct(Long id, Long sellerId, Long salesPostId, Long categoryId, String name, String description,
                            Long basePrice, ProductStatus status, LocalDateTime createDate, LocalDateTime modifyDate) {
        super(id, createDate, modifyDate);
        this.sellerId = sellerId;
        this.salesPostId = salesPostId;
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.status = status;
    }

    public void addProductImage(List<UploadedImage> uploadedImageList) {
//        for (UploadedImage image : uploadedImageList) {
//            productImageList.add(
//                    new SalesPostProductImage(
//                            this.getSellerId(),
//                            this,
//                            image.getS3Key(),
//                            image.getImageSeq()
//                    )
//            );
//        }
    }

    public SalesPostProduct delete() {
        this.status = ProductStatus.DELETED;
        return this;
    }
}
