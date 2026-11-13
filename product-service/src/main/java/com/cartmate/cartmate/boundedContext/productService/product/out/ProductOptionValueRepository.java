package com.cartmate.cartmate.boundedContext.productService.product.out;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductOptionValue;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionValueRepository extends JpaRepository<ProductOptionValue, Long> {

    // 요청의 옵션 ID(productOptionId)로 옵션 값을 조회한다. 없으면 PRODUCT_OPTION_VALUE_NOT_FOUND
    default ProductOptionValue getOptionValue(Long optionId) {
        if (optionId == null) {
            throw new ServiceException(ProductErrorCode.PRODUCT_OPTION_VALUE_NOT_FOUND);
        }
        return findById(optionId)
                .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_OPTION_VALUE_NOT_FOUND));
    }
}
