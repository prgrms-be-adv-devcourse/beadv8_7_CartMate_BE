package com.cartmate.cartmate.boundedContext.productService.product.out;

import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductOptionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductOptionTypeRepository extends JpaRepository<ProductOptionType, Long> {

    // 조회 API: 옵션 종류 전체 (ID 순 = data.sql 순)
    List<ProductOptionType> findAllByOrderByIdAsc();
}
