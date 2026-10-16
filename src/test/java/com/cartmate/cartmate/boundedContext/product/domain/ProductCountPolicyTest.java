package com.cartmate.cartmate.boundedContext.product.domain;

import com.cartmate.cartmate.shared.product.dto.create.CreateProduct;
import com.cartmate.cartmate.shared.product.dto.create.CreateProductImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCountPolicyTest {
    private final ProductCountPolicy policy = new ProductCountPolicy();

    @Nested
    @DisplayName("상품 개수 검증")
    class ProductCount {

        @Test
        @DisplayName("상품 리스트가 null이면 실패한다")
        void failWhenNull() {
            assertThat(policy.isSafeProductCount(null)).isFalse();
        }

        @Test
        @DisplayName("상품이 0개면 실패한다")
        void failWhenEmpty() {
            assertThat(policy.isSafeProductCount(List.of())).isFalse();
        }

        @ParameterizedTest(name = "상품 {0}개는 통과한다")
        @ValueSource(ints = {1, 4, 5})
        void passWithinLimit(int count) {
            assertThat(policy.isSafeProductCount(products(count, 1))).isTrue();
        }

        @ParameterizedTest(name = "상품 {0}개는 실패한다")
        @ValueSource(ints = {6, 10})
        void failOverLimit(int count) {
            assertThat(policy.isSafeProductCount(products(count, 1))).isFalse();
        }
    }

    @Nested
    @DisplayName("전체 사진 수 검증")
    class ImageCount {

        @Test
        @DisplayName("사진이 한 장도 없으면 실패한다")
        void failWhenNoImage() {
            assertThat(policy.isSafeProductImageCount(products(2, 0))).isFalse();
        }

        @ParameterizedTest(name = "사진 총 {0}장은 통과한다")
        @ValueSource(ints = {1, 19, 20})
        void passWithinLimit(int imageCount) {
            assertThat(policy.isSafeProductImageCount(List.of(product(imageCount)))).isTrue();
        }

        @ParameterizedTest(name = "사진 총 {0}장은 실패한다")
        @ValueSource(ints = {21, 30})
        void failOverLimit(int imageCount) {
            assertThat(policy.isSafeProductImageCount(List.of(product(imageCount)))).isFalse();
        }

        @Test
        @DisplayName("여러 상품의 사진을 합산해서 판단한다 (10장 + 10장 = 20장 통과)")
        void sumAcrossProductsWithinLimit() {
            List<CreateProduct> products = List.of(product(10), product(10));

            assertThat(policy.isSafeProductImageCount(products)).isTrue();
        }

        @Test
        @DisplayName("여러 상품의 사진을 합산해서 판단한다 (10장 + 11장 = 21장 실패)")
        void sumAcrossProductsOverLimit() {
            List<CreateProduct> products = List.of(product(10), product(11));

            assertThat(policy.isSafeProductImageCount(products)).isFalse();
        }

        @Test
        @DisplayName("일부 상품에 사진이 없어도 전체 합이 범위 안이면 통과한다")
        void passWhenSomeProductHasNoImage() {
            List<CreateProduct> products = List.of(product(0), product(3));

            assertThat(policy.isSafeProductImageCount(products)).isTrue();
        }
    }

    // ===== 테스트용 데이터 =====

    private List<CreateProduct> products(int productCount, int imagesPerProduct) {
        return IntStream.range(0, productCount)
                .mapToObj(i -> product(imagesPerProduct))
                .toList();
    }

    private CreateProduct product(int imageCount) {
        List<CreateProductImage> images = new ArrayList<>();
        for (int i = 0; i < imageCount; i++) {
            images.add(new CreateProductImage(0L, 0L, 0L, null, i, null));
        }
        return new CreateProduct(0L, 0L, 0L, 1L, "상품", "설명", 10000L, images);
    }
}