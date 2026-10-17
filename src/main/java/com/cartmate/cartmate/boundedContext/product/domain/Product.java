package com.cartmate.cartmate.boundedContext.product.domain;

import com.cartmate.cartmate.boundedContext.product.dto.UploadedImage;
import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import com.cartmate.cartmate.shared.product.dto.ProductRollbackRequest;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "PRODUCT_PRODUCT")
@NoArgsConstructor
@Getter
public class Product extends BaseIdAndTime {
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
    private List<ProductImage> productImageList = new ArrayList<>();


    public Product(Long sellerId, Long salesPostId, Long categoryId, String name,
                   String description, Long basePrice, ProductStatus status) {
        this.sellerId = sellerId;
        this.salesPostId = salesPostId;
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.status = status;
    }

    public ProductRollbackRequest toRollbackDto() {
        return new ProductRollbackRequest(this.getId());
    }


    public void addProductImage(List<UploadedImage> uploadedImageList) {
        for (UploadedImage image : uploadedImageList) {
            productImageList.add(
                    new ProductImage(
                            this.getSellerId(),
                            this,
                            image.getS3Key(),
                            image.getImageSeq()
                    )
            );
        }
    }

    public Product delete() {
        this.status = ProductStatus.DELETED;
        return this;
    }
}
