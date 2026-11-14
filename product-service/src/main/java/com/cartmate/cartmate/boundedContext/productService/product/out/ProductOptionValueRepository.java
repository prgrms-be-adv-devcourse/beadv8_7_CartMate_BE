package com.cartmate.cartmate.boundedContext.productService.product.out;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductOptionValue;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductOptionValueRepository extends JpaRepository<ProductOptionValue, Long> {

    // 조회 API: 상품옵션 전체 / 옵션 종류 하나의 상품옵션 (ID 순 = data.sql 순)
    List<ProductOptionValue> findAllByOrderByIdAsc();

    List<ProductOptionValue> findByType_IdOrderByIdAsc(Long typeId);

    // 요청의 옵션 ID(productOptionId)로 옵션 값을 조회한다. 없으면 PRODUCT_OPTION_VALUE_NOT_FOUND
    default ProductOptionValue getOptionValue(Long optionId) {
        if (optionId == null) {
            throw new ServiceException(ProductErrorCode.PRODUCT_OPTION_VALUE_NOT_FOUND);
        }
        return findById(optionId)
                .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_OPTION_VALUE_NOT_FOUND));
    }

    // 재고 하나를 이루는 상품옵션들을 요청 순서대로 조회한다(재고 이름 "265 / 블랙"이 이 순서를 따른다)
    default List<ProductOptionValue> getOptionValues(List<Long> optionIds) {
        if (optionIds == null) {
            return List.of();
        }
        return optionIds.stream().map(this::getOptionValue).toList();
    }
}
