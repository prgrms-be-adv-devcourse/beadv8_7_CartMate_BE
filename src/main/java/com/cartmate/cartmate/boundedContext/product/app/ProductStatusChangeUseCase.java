package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import com.cartmate.cartmate.global.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductStatusChangeUseCase {
    private static final Set<ProductStatus> TARGETS =
            EnumSet.of(ProductStatus.ON_SALE, ProductStatus.PAUSED, ProductStatus.SUSPENDED);

    private final ProductRepository productRepository;

    // 판매글 단위 판매 상태 변경. 판매 중이 아니게 되면 기존 구매 가능 판단(ON_SALE)에 걸려
    // 장바구니·주문·공동구매 담기가 바로 막힌다. 이미 그 상태면 건너뛰므로 다시 보내도 안전하다.
    @Transactional
    public void changeStatusByPost(Long postId, ProductStatus to) {
        if (!TARGETS.contains(to)) {
            throw new ServiceException(ProductErrorCode.PRODUCT_UPDATE_INVALID);
        }
        List<Product> products = productRepository.findBySalesPostId(postId);
        if (products.isEmpty()) {
            throw new ServiceException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        products.forEach(p -> p.changeSaleStatusByPost(to));
    }
}