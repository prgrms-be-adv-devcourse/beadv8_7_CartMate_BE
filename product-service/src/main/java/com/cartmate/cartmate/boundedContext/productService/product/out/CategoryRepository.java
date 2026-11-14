package com.cartmate.cartmate.boundedContext.productService.product.out;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Category;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // 조회 API: 카테고리 전체 (ID 순 = data.sql 순)
    List<Category> findAllByOrderByIdAsc();

    // 상품 등록 · 수정 요청의 categoryId로 카테고리를 조회한다. 없으면 PRODUCT_CATEGORY_NOT_FOUND
    default Category getCategory(Long categoryId) {
        if (categoryId == null) {
            throw new ServiceException(ProductErrorCode.PRODUCT_CATEGORY_NOT_FOUND);
        }
        return findById(categoryId)
                .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_CATEGORY_NOT_FOUND));
    }
}
