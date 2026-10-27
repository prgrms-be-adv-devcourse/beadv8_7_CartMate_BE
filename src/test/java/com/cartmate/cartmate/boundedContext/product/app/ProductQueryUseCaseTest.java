package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.product.domain.ProductUpdateStatus;
import com.cartmate.cartmate.boundedContext.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import com.cartmate.cartmate.shared.product.dto.ProductForPurchase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProductQueryUseCaseTest {

    private static final Long SELLER = 10L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    private ProductQueryUseCase queryUseCase;

    @BeforeEach
    void setUp() {
        queryUseCase = new ProductQueryUseCase(productRepository, productImageRepository, new FakeImageStorage());
    }

    @Test
    @DisplayName("판매중 상품은 구매 가능, 그 외 상태는 구매 불가로 조회되고 없는 상품은 결과에서 빠진다")
    void findProductsForPurchase() {
        Product onSale = product(1L, "사과", 1000L, ProductStatus.ON_SALE);
        Product paused = product(2L, "배", 2000L, ProductStatus.PAUSED);
        given(productRepository.findAllById(List.of(1L, 2L, 3L))).willReturn(List.of(onSale, paused));
        given(productImageRepository.findByProductIdIn(List.of(1L, 2L))).willReturn(List.of(
                new ProductImage(SELLER, onSale, "deleted-first", 0, ProductStatus.DELETED, true),
                new ProductImage(SELLER, onSale, "active-third", 2, ProductStatus.ACTIVE, true),
                new ProductImage(SELLER, onSale, "active-second", 1, ProductStatus.ACTIVE, true)
        ));

        List<ProductForPurchase> result = queryUseCase.findProductsForPurchase(List.of(1L, 2L, 3L, 1L));

        assertThat(result).containsExactly(
                new ProductForPurchase(1L, SELLER, "사과", 1000L, true, "http://test/active-second"),
                new ProductForPurchase(2L, SELLER, "배", 2000L, false, null)
        );
    }

    private Product product(Long id, String name, Long basePrice, ProductStatus status) {
        Product product = new Product(SELLER, 100L, 1L, name, null, basePrice, status, true);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
