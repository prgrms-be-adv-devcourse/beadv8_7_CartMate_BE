package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.product.dto.CategoryResponse;
import com.cartmate.cartmate.boundedContext.productService.product.out.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 상품 등록 화면의 카테고리 선택 목록
@Service
@RequiredArgsConstructor
public class CategoryQueryUseCase {
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAllByOrderByIdAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
