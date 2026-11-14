package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductOptionType;
import com.cartmate.cartmate.boundedContext.productService.product.dto.ProductOptionTypeResponse;
import com.cartmate.cartmate.boundedContext.productService.product.dto.ProductOptionValueResponse;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionTypeRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionValueRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 상품 등록 화면의 옵션 선택 목록: 옵션 종류와 그 종류의 상품옵션
@Service
@RequiredArgsConstructor
public class ProductOptionQueryUseCase {
    private final ProductOptionTypeRepository productOptionTypeRepository;
    private final ProductOptionValueRepository productOptionValueRepository;

    // 옵션 종류 전체 + 종류별 상품옵션. 쿼리 두 번으로 가져와 종류별로 묶는다
    @Transactional(readOnly = true)
    public List<ProductOptionTypeResponse> findAll() {
        Map<Long, List<ProductOptionValueResponse>> valuesByType = productOptionValueRepository.findAllByOrderByIdAsc()
                .stream()
                .collect(Collectors.groupingBy(
                        value -> value.getType().getId(),   // 프록시에서 ID만 꺼내므로 추가 조회 없음
                        Collectors.mapping(ProductOptionValueResponse::from, Collectors.toList())));

        return productOptionTypeRepository.findAllByOrderByIdAsc().stream()
                .map(type -> new ProductOptionTypeResponse(
                        type.getId(), type.getName(), valuesByType.getOrDefault(type.getId(), List.of())))
                .toList();
    }

    // 옵션 종류 하나의 상품옵션 (옵션 종류를 고르면 옆 선택 목록을 채우는 용도). 없는 종류면 PRODUCT_OPTION_TYPE_NOT_FOUND
    @Transactional(readOnly = true)
    public ProductOptionTypeResponse findByType(Long typeId) {
        ProductOptionType type = productOptionTypeRepository.findById(typeId)
                .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_OPTION_TYPE_NOT_FOUND));
        List<ProductOptionValueResponse> options = productOptionValueRepository.findByType_IdOrderByIdAsc(typeId)
                .stream()
                .map(ProductOptionValueResponse::from)
                .toList();
        return new ProductOptionTypeResponse(type.getId(), type.getName(), options);
    }
}
