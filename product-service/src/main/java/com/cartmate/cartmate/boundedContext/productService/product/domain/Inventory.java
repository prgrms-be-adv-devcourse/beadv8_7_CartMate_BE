package com.cartmate.cartmate.boundedContext.productService.product.domain;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 재고: 상품의 판매 단위(SKU) 하나 = 상품옵션 조합 하나 (예: 축구화 A의 "265 / 블랙").
 * 이 재고를 이루는 상품옵션은 선택된 옵션(ProductOption)이 재고를 참조해 저장한다.
 * 조합(어떤 상품옵션으로 이뤄졌는지)은 만든 뒤 바꾸지 않는다. 조합을 바꾸려면 재고를 지우고 새로 추가한다.
 */
@Entity
@Table(name = "PRODUCT_INVENTORY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory extends BaseIdAndTime {

    static final String NO_OPTION_SKU_NAME = "기본";
    private static final String SKU_NAME_DELIMITER = " / ";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // 제품정보 조합. 상품옵션 이름으로 서버가 만든다 (예: "265 / 블랙", 옵션이 없으면 "기본")
    @Column(name = "sku_name", nullable = false, length = 200)
    private String skuName;

    // 옵션적용가격(최종) = 상품 기본가 + 추가금액. 서버가 계산해 저장한다
    @Column(name = "applied_price", nullable = false)
    private Long appliedPrice;

    // 추가금액. 판매자가 화면에서 입력한다
    @Column(name = "additional_price", nullable = false)
    private Long additionalPrice;

    // 실제 재고 수량
    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    // 예약 재고 수량. 주문 연동(재고 예약 · 차감)에서 쓴다
    @Column(name = "reserved_quantity", nullable = false)
    @ColumnDefault("0")
    private Integer reservedQuantity;

    // 재고는 지우지 않고 상태로 남긴다(재고 ID를 가리키는 장바구니 · 주문 이력이 끊기지 않게)
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVE'")
    private ProductStatus status;

    // 이 재고를 이루는 선택된 옵션. 상품 행을 지울 때(등록 보상) 재고보다 먼저 지워지도록 REMOVE를 둔다
    @OneToMany(mappedBy = "inventory", cascade = {CascadeType.PERSIST, CascadeType.REMOVE})
    private List<ProductOption> optionList = new ArrayList<>();

    private Inventory(Product product, Long additionalPrice, Integer stockQuantity) {
        validateQuantities(additionalPrice, stockQuantity);
        this.product = product;
        this.additionalPrice = additionalPrice;
        this.stockQuantity = stockQuantity;
        this.reservedQuantity = 0;
        this.status = ProductStatus.ACTIVE;
        this.appliedPrice = product.getBasePrice() + additionalPrice;
    }

    /**
     * 상품옵션 조합으로 재고를 만들고, 상품옵션마다 선택된 옵션을 붙인다.
     * 한 재고에 같은 옵션 종류가 두 번 들어갈 수 없다 (예: 265와 270을 함께 가질 수 없음).
     */
    public static Inventory create(Product product, List<ProductOptionValue> optionValues,
                                   Long additionalPrice, Integer stockQuantity) {
        validateOptionTypes(optionValues);

        Inventory inventory = new Inventory(product, additionalPrice, stockQuantity);
        inventory.skuName = optionValues.isEmpty()
                ? NO_OPTION_SKU_NAME
                : optionValues.stream().map(ProductOptionValue::getName).collect(Collectors.joining(SKU_NAME_DELIMITER));
        optionValues.forEach(value ->
                inventory.optionList.add(new ProductOption(product, inventory, value, ProductStatus.ACTIVE)));
        return inventory;
    }

    // 판매자가 바꿀 수 있는 값: 추가금액 · 재고 수량. 최종 가격도 다시 계산한다
    public void change(Long additionalPrice, Integer stockQuantity) {
        validateQuantities(additionalPrice, stockQuantity);
        this.additionalPrice = additionalPrice;
        this.stockQuantity = stockQuantity;
        recalculateAppliedPrice();
    }

    // 상품 기본가가 바뀌면 최종 가격을 다시 계산한다
    public void recalculateAppliedPrice() {
        this.appliedPrice = product.getBasePrice() + additionalPrice;
    }

    public Set<Long> optionValueIds() {
        return optionList.stream()
                .map(ProductOption::getProductOptionId)
                .collect(Collectors.toSet());
    }

    public boolean isActive() {
        return status == ProductStatus.ACTIVE;
    }

    public Inventory delete() {
        this.status = ProductStatus.DELETED;
        optionList.forEach(ProductOption::delete);
        return this;
    }

    // 수정 실패 복구: 스냅샷의 추가금액 · 재고 수량으로 같은 행(같은 ID)을 되살린다
    public Inventory restore(Long additionalPrice, Integer stockQuantity) {
        change(additionalPrice, stockQuantity);
        this.status = ProductStatus.ACTIVE;
        optionList.forEach(ProductOption::restore);
        return this;
    }

    private static void validateQuantities(Long additionalPrice, Integer stockQuantity) {
        if (additionalPrice == null || additionalPrice < 0 || stockQuantity == null || stockQuantity < 0) {
            throw new ServiceException(ProductErrorCode.PRODUCT_INVENTORY_INVALID);
        }
    }

    private static void validateOptionTypes(List<ProductOptionValue> optionValues) {
        Set<Long> typeIds = new HashSet<>();
        for (ProductOptionValue value : optionValues) {
            if (!typeIds.add(value.getType().getId())) {
                throw new ServiceException(ProductErrorCode.PRODUCT_INVENTORY_DUPLICATE_OPTION_TYPE);
            }
        }
    }
}
