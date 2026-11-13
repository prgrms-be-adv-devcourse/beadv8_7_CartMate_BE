package com.cartmate.cartmate.boundedContext.productService.product.domain;

import com.cartmate.cartmate.boundedContext.productService.product.dto.*;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    @OneToMany(mappedBy = "product", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval = true)
    private List<ProductImage> productImageList = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval = true)
    private List<ProductOption> productOptionList = new ArrayList<>();

    // 선택된 옵션 목록보다 뒤에 둔다: 상품 행을 지울 때(등록 보상) 옵션 → 재고 순으로 지워져 FK가 깨지지 않는다
    @OneToMany(mappedBy = "product", cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval = true)
    private List<Inventory> inventoryList = new ArrayList<>();

    public Product(Long sellerId, Long salesPostId, Long categoryId, String name, String description, Long basePrice,
                   ProductStatus status) {
        this.sellerId = sellerId;
        this.salesPostId = salesPostId;
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.status = status;
    }

    public SyncProduct toSyncProduct() {
        return toSyncProduct(List.of());
    }

    // 수정 응답: 이번 수정에서 빠진 재고 ID를 함께 돌려준다(post가 수정 완료 때 InventoryRemovedEvent로 알린다)
    public SyncProduct toSyncProduct(List<Long> removedInventoryIds) {
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
                optionList,
                new ArrayList<>(removedInventoryIds)
        );
    }



    public void addProductImage(List<UploadedImage> uploadedImageList) {
        for (UploadedImage image : uploadedImageList) {
            productImageList.add(
                    new ProductImage(
                            this.getSellerId(),
                            this,
                            image.getS3Key(),
                            image.getImageSeq(),
                            ProductStatus.ACTIVE
                    )
            );
        }
    }

    public Product delete() {
        this.status = ProductStatus.DELETED;
        this.productImageList.forEach(ProductImage::delete);
        this.inventoryList.forEach(Inventory::delete);   // 재고와 그 선택된 옵션을 함께 지운다
        this.productOptionList.forEach(ProductOption::delete);
        return this;
    }

    // 기본가가 바뀌면 재고의 최종 가격(기본가 + 추가금액)도 다시 계산한다
    public void update(Long categoryId, String name, String description, Long basePrice) {
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
        this.inventoryList.forEach(Inventory::recalculateAppliedPrice);
    }

    public void addImage(ProductImage newImage) {
        this.getProductImageList().add(newImage);
    }

    /**
     * 상품옵션 조합으로 재고를 추가하고, 그 재고에서 분리된 선택된 옵션도 상품에 붙인다.
     * 살아 있는 재고 중 같은 조합이 있으면 거부한다.
     */
    public Inventory addInventory(List<ProductOptionValue> optionValues, Long additionalPrice, Integer stockQuantity) {
        Inventory inventory = Inventory.create(this, optionValues, additionalPrice, stockQuantity);
        Set<Long> combination = inventory.optionValueIds();
        boolean duplicated = inventoryList.stream()
                .filter(Inventory::isActive)
                .anyMatch(existing -> existing.optionValueIds().equals(combination));
        if (duplicated) {
            throw new ServiceException(ProductErrorCode.PRODUCT_INVENTORY_DUPLICATE_COMBINATION);
        }
        inventoryList.add(inventory);
        productOptionList.addAll(inventory.getOptionList());
        return inventory;
    }

    public List<Inventory> activeInventories() {
        return inventoryList.stream().filter(Inventory::isActive).toList();
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
