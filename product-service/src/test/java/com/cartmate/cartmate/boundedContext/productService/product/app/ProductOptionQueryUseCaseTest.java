package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.product.dto.ProductOptionTypeResponse;
import com.cartmate.cartmate.boundedContext.productService.product.dto.ProductOptionValueResponse;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 옵션 종류 · 상품옵션 조회: data.sql 초기 데이터가 옵션 종류별로 묶여 ID 순으로 나온다.
 */
@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class ProductOptionQueryUseCaseTest {

    @Autowired
    ProductOptionQueryUseCase productOptionQueryUseCase;

    @Test
    @DisplayName("전체 조회: 옵션 종류 5개가 ID 순으로 나오고, 각 종류에 그 종류의 상품옵션만 묶인다")
    void findAllGroupsOptionsByType() {
        // 다른 테스트가 넣은 상품옵션이 있을 수 있어 시드 범위(옵션 종류 1~5)만 본다
        List<ProductOptionTypeResponse> types = productOptionQueryUseCase.findAll().stream()
                .filter(type -> type.typeId() <= 5)
                .toList();

        assertThat(types)
                .extracting(ProductOptionTypeResponse::typeId, ProductOptionTypeResponse::typeName,
                        type -> type.options().size())
                .containsExactly(
                        tuple(1L, "의류 사이즈", 7),
                        tuple(2L, "색상", 10),
                        tuple(3L, "신발 사이즈", 17),
                        tuple(4L, "공 사이즈", 3),
                        tuple(5L, "골키퍼 장갑 사이즈", 6));
        assertThat(types.getFirst().options())
                .extracting(ProductOptionValueResponse::optionId, ProductOptionValueResponse::name)
                .containsExactly(tuple(1L, "XS"), tuple(2L, "S"), tuple(3L, "M"), tuple(4L, "L"),
                        tuple(5L, "XL"), tuple(6L, "2XL"), tuple(7L, "3XL"));
    }

    @Test
    @DisplayName("종류 하나 조회: 그 옵션 종류의 상품옵션만 ID 순으로 나온다")
    void findByTypeReturnsOnlyThatType() {
        ProductOptionTypeResponse ball = productOptionQueryUseCase.findByType(4L);

        assertThat(ball.typeId()).isEqualTo(4L);
        assertThat(ball.typeName()).isEqualTo("공 사이즈");
        assertThat(ball.options())
                .extracting(ProductOptionValueResponse::optionId, ProductOptionValueResponse::name)
                .containsExactly(tuple(35L, "3호"), tuple(36L, "4호"), tuple(37L, "5호"));
    }

    @Test
    @DisplayName("종류 하나 조회: 없는 옵션 종류면 PRODUCT_OPTION_TYPE_NOT_FOUND")
    void findByTypeRejectsUnknownType() {
        assertThatThrownBy(() -> productOptionQueryUseCase.findByType(999L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_OPTION_TYPE_NOT_FOUND");
    }
}
