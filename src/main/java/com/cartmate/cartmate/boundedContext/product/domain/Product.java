package com.cartmate.cartmate.boundedContext.product.domain;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.code.ProductErrorCode;
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

import java.time.LocalDateTime;
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

    public Product(Long sellerId, Long salesPostId, Long categoryId, String name, String description, Long basePrice,
                   ProductStatus status, boolean syncStatus) {
        this.sellerId = sellerId;
        this.salesPostId = salesPostId;
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.status = status;
        this.syncStatus = syncStatus;
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

    // 판매글 단위 판매 상태 변경. 판매 관련 상태(ON_SALE·PAUSED·SUSPENDED)일 때만 바꾼다.
    // 삭제·등록 중 상품은 건드리지 않는다. 같은 상태면 그대로(멱등).
    // 판매글 단위 판매 상태 변경. 바꿀 대상과 지금 상태가 모두 '판매 관련' 상태일 때만 바꾼다.
    // 대상이 판매 관련 상태가 아니면 잘못된 요청, 지금 상태가 아니면(삭제·등록 중 등) 그대로 둔다. 같은 상태면 그대로(멱등).
    public void changeSaleStatusByPost(ProductStatus to) {
        if (!to.isSaleControllable()) {
            throw new ServiceException(ProductErrorCode.PRODUCT_UPDATE_INVALID);
        }
        if (status.isSaleControllable()) {
            this.status = to;
        }
    }

}
