package com.cartmate.cartmate.boundedContext.product.domain;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPostUpdateStatus;
import com.cartmate.cartmate.boundedContext.product.dto.UploadedImage;
import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import com.cartmate.cartmate.shared.product.dto.CreateProductOption;
import com.cartmate.cartmate.shared.product.dto.SyncProduct;
import com.cartmate.cartmate.shared.product.dto.SyncProductImage;
import com.cartmate.cartmate.shared.product.dto.SyncProductOption;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private ProductStatus status;

    @Column(nullable = false)
    private boolean syncStatus;

    @OneToMany(mappedBy = "product", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval = true)
    private List<ProductImage> productImageList = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval = true)
    private List<ProductOption> productOptionList = new ArrayList<>();

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private ProductUpdateStatus updateStatus;


    public Product(Long sellerId, Long salesPostId, Long categoryId, String name, String description,
                   Long basePrice, ProductStatus status, boolean syncStatus, ProductUpdateStatus updateStatus) {
        this.sellerId = sellerId;
        this.salesPostId = salesPostId;
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.status = status;
        this.syncStatus = syncStatus;
        this.updateStatus = updateStatus;
    }

    public SyncProduct toSyncProduct() {
        List<SyncProductImage> imageList = this.getProductImageList()
                .stream()
                .map(ProductImage::toSyncProductImage)
                .toList();
        List<SyncProductOption> optionList = this.getProductOptionList()
                .stream()
                .map(ProductOption::toSyncProductOption)
                .toList();

        return new SyncProduct(
                this.getId(),
                this.getSellerId(),
                this.getSalesPostId(),
                this.getCategoryId(),
                this.getName(),
                this.getDescription(),
                this.getBasePrice(),
                this.getCreateDate(),
                this.getModifyDate(),
                imageList,
                optionList
        );
    }


    public void addProductOption(List<CreateProductOption> optionList) {
        for (CreateProductOption option : optionList) {
            productOptionList.add(new ProductOption(
                    this,
                    option.getProductTypeId(),
                    option.getProductOptionId()
            ));
        }
    }

    public void addProductImage(List<UploadedImage> uploadedImageList) {
        for (UploadedImage image : uploadedImageList) {
            productImageList.add(
                    new ProductImage(
                            this.getSellerId(),
                            this,
                            image.getS3Key(),
                            image.getImageSeq(),
                            ProductStatus.ACTIVE,
                            false
                    )
            );
        }
    }

    public Product delete() {
        this.status = ProductStatus.DELETED;
        this.productImageList.forEach(ProductImage::delete);
        productOptionList.clear();
        return this;
    }

    public Product updateSyncStatus() {
        this.syncStatus = true;
        productImageList.forEach(ProductImage::updateSyncStatus);
        return this;
    }

    public void update(Long categoryId, String name, String description, Long basePrice) {
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
    }

    public void addImage(ProductImage newImage) {
        this.getProductImageList().add(newImage);
    }

    public void addOption(ProductOption newOption) {
        this.getProductOptionList().add(newOption);
    }
}
