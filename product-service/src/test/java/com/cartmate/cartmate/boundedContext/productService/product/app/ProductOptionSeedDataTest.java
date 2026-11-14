package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.product.dto.CategoryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 옵션 종류 · 상품옵션 초기 데이터(data.sql): 서버가 시작될 때 들어가고, 다시 실행해도 오류 없이 그대로다.
 */
@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class ProductOptionSeedDataTest {

    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    DataSource dataSource;

    @Autowired
    CategoryQueryUseCase categoryQueryUseCase;

    @Test
    @DisplayName("시작할 때 카테고리 4개가 고정 ID(프론트가 쓰던 1 ~ 4)와 한글 이름으로 들어가고, 조회하면 ID 순으로 나온다")
    void seedsCategories() {
        assertThat(categoryQueryUseCase.findAll().stream().filter(category -> category.categoryId() <= 4))
                .extracting(CategoryResponse::categoryId, CategoryResponse::name)
                .containsExactly(
                        tuple(1L, "유니폼 / 의류"),
                        tuple(2L, "운동 용품"),
                        tuple(3L, "신발 / 보호구"),
                        tuple(4L, "단체 액세서리"));
    }

    @Test
    @DisplayName("시작할 때 옵션 종류 5개가 고정 ID와 한글 이름으로 들어간다")
    void seedsOptionTypes() {
        List<Map<String, Object>> types = jdbcTemplate.queryForList(
                "SELECT id, name FROM product_product_option_type WHERE id BETWEEN 1 AND 5 ORDER BY id");

        assertThat(types).extracting(row -> row.get("name"))
                .containsExactly("의류 사이즈", "색상", "신발 사이즈", "공 사이즈", "골키퍼 장갑 사이즈");
    }

    @Test
    @DisplayName("시작할 때 상품옵션 43개가 1번부터 옵션 종류별로 들어간다")
    void seedsOptionValues() {
        List<Map<String, Object>> counts = jdbcTemplate.queryForList(
                "SELECT type_id, COUNT(*) AS cnt, MIN(id) AS min_id, MAX(id) AS max_id "
                        + "FROM product_product_option_value WHERE id BETWEEN 1 AND 43 GROUP BY type_id ORDER BY type_id");

        assertThat(counts).extracting(row -> ((Number) row.get("type_id")).longValue(),
                        row -> ((Number) row.get("cnt")).longValue(),
                        row -> ((Number) row.get("min_id")).longValue(),
                        row -> ((Number) row.get("max_id")).longValue())
                .containsExactly(
                        tuple(1L, 7L, 1L, 7L),     // 의류 사이즈 XS ~ 3XL
                        tuple(2L, 10L, 8L, 17L),   // 색상
                        tuple(3L, 17L, 18L, 34L),  // 신발 사이즈 220 ~ 300
                        tuple(4L, 3L, 35L, 37L),   // 공 사이즈 3 ~ 5호
                        tuple(5L, 6L, 38L, 43L));  // 골키퍼 장갑 사이즈 6 ~ 11

        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM product_product_option_value WHERE id = 8", String.class)).isEqualTo("블랙");
    }

    @Test
    @DisplayName("data.sql을 다시 실행해도(서버 재시작) 오류 없이 기존 데이터가 그대로다")
    void rerunIsIdempotent() {
        jdbcTemplate.update("UPDATE product_product_option_value SET name = '블랙(수정)' WHERE id = 8");
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("data.sql"));
            populator.setSqlScriptEncoding("UTF-8");

            assertThatCode(() -> populator.execute(dataSource)).doesNotThrowAnyException();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT name FROM product_product_option_value WHERE id = 8", String.class))
                    .isEqualTo("블랙(수정)");   // 이미 있는 ID는 덮어쓰지 않는다
        } finally {
            jdbcTemplate.update("UPDATE product_product_option_value SET name = '블랙' WHERE id = 8");
        }
    }
}
