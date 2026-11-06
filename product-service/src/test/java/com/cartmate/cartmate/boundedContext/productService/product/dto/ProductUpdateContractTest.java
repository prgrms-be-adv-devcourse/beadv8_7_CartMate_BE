package com.cartmate.cartmate.boundedContext.productService.product.dto;

import com.cartmate.cartmate.shared.product.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 판매글 수정 흐름의 post ↔ product JSON 계약: 수정 요청(post → product), 복구 요청(post → product), 조회 응답(product → post).
 * 두 쪽 dto를 따로 관리하므로 필드 이름이 어긋나면 값이 조용히 빠진다. 그걸 여기서 잡는다.
 */
class ProductUpdateContractTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 6, 10, 0);

    // Spring Boot 기본처럼 모르는 필드는 무시한다. 수정 요청에는 post에만 있는 sellerId · title · content가 실려 간다
    private final JsonMapper lenient = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    // 필드 구성이 같아야 하는 구간은 모르는 필드가 오면 실패하게 한다
    private final JsonMapper strict = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    @DisplayName("수정 요청: post의 판매글 수정 요청이 product의 상품 수정 요청으로 값이 그대로 옮겨진다")
    void updateRequestIsReadAsProductRequest() {
        var sent = new com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostRequest(
                10L, 7L, "새 제목", "새 내용", List.of(
                new com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostProduct(
                        1L, 30L, "아이폰", "부사 5kg", 25000L,
                        List.of(new com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostProductImage(
                                100L, 1L, "products/a.jpg", 0)),
                        List.of(new com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostProductOption(
                                200L, 1L, 11L)))));

        UpdateProductsRequest received = lenient.readValue(lenient.writeValueAsString(sent), UpdateProductsRequest.class);

        assertThat(received.getId()).isEqualTo(10L);
        assertThat(received.getProductList()).hasSize(1);
        UpdateProduct product = received.getProductList().getFirst();
        assertThat(product.getId()).isEqualTo(1L);
        assertThat(product.getCategoryId()).isEqualTo(30L);
        assertThat(product.getName()).isEqualTo("아이폰");
        assertThat(product.getDescription()).isEqualTo("부사 5kg");
        assertThat(product.getBasePrice()).isEqualTo(25000L);
        assertThat(product.getProductImageList())
                .extracting(UpdateProductImage::getId, UpdateProductImage::getProductId,
                        UpdateProductImage::getS3Key, UpdateProductImage::getImageSeq)
                .containsExactly(tuple(100L, 1L, "products/a.jpg", 0));
        assertThat(product.getProductOptionList())
                .extracting(UpdateProductOption::getId, UpdateProductOption::getProductTypeId, UpdateProductOption::getProductOptionId)
                .containsExactly(tuple(200L, 1L, 11L));
    }

    @Test
    @DisplayName("수정 요청: post에만 있는 필드(sellerId · title · content)를 빼면 두 dto의 JSON 필드 구성이 같다")
    void updateRequestHasSameProductFields() {
        var sent = new com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostRequest(
                10L, 7L, "새 제목", "새 내용", List.of(
                new com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostProduct(
                        1L, 30L, "아이폰", "부사 5kg", 25000L,
                        List.of(new com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostProductImage(
                                100L, 1L, "products/a.jpg", 0)),
                        List.of(new com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostProductOption(
                                200L, 1L, 11L)))));

        List<String> sentPaths = fieldPaths(lenient.valueToTree(sent), "");
        List<String> readPaths = fieldPaths(lenient.valueToTree(
                lenient.readValue(lenient.writeValueAsString(sent), UpdateProductsRequest.class)), "");

        sentPaths.removeAll(List.of("sellerId", "title", "content"));
        assertThat(readPaths).containsExactlyInAnyOrderElementsOf(sentPaths);
    }

    @Test
    @DisplayName("복구 요청: post가 보낸 복구 요청을 product가 같은 값 · 같은 필드 구성으로 읽는다")
    void restoreRequestIsReadAsProductRequest() {
        var sent = new com.cartmate.cartmate.boundedContext.productService.post.dto.RestoreProductsRequest(List.of(
                new com.cartmate.cartmate.boundedContext.productService.post.dto.RestoreProduct(
                        1L, 30L, "아이폰", "부사 5kg", 25000L,
                        List.of(new com.cartmate.cartmate.boundedContext.productService.post.dto.RestoreProductImage(100L, 0),
                                new com.cartmate.cartmate.boundedContext.productService.post.dto.RestoreProductImage(101L, 1)),
                        List.of(new com.cartmate.cartmate.boundedContext.productService.post.dto.RestoreProductOption(1L, 11L)))));

        String json = strict.writeValueAsString(sent);
        RestoreProductsRequest received = strict.readValue(json, RestoreProductsRequest.class);

        RestoreProduct product = received.getProductList().getFirst();
        assertThat(product.getId()).isEqualTo(1L);
        assertThat(product.getCategoryId()).isEqualTo(30L);
        assertThat(product.getName()).isEqualTo("아이폰");
        assertThat(product.getDescription()).isEqualTo("부사 5kg");
        assertThat(product.getBasePrice()).isEqualTo(25000L);
        assertThat(product.getProductImageList())
                .extracting(RestoreProductImage::getId, RestoreProductImage::getImageSeq)
                .containsExactly(tuple(100L, 0), tuple(101L, 1));
        assertThat(product.getProductOptionList())
                .extracting(RestoreProductOption::getProductTypeId, RestoreProductOption::getProductOptionId)
                .containsExactly(tuple(1L, 11L));

        assertThat(fieldPaths(strict.valueToTree(received), ""))
                .containsExactlyInAnyOrderElementsOf(fieldPaths(strict.readTree(json), ""));
    }

    @Test
    @DisplayName("조회 응답: product가 보낸 상품 조회 응답을 post가 같은 값 · 같은 필드 구성으로 읽는다")
    void selectResponseIsReadByPost() {
        var sent = new SelectProductResponse(1L, 10L, 30L, "아이폰", "부사 5kg", 25000L, ProductStatus.ON_SALE,
                List.of(new SelectProductImage(100L, "products/a.jpg", 0, ProductStatus.ACTIVE, AT, AT)),
                List.of(new SelectProductOption(200L, 1L, 11L, AT, AT)));

        String json = strict.writeValueAsString(sent);
        var received = strict.readValue(json,
                com.cartmate.cartmate.boundedContext.productService.post.dto.SelectProductResponse.class);

        assertThat(received.getId()).isEqualTo(1L);
        assertThat(received.getSalesPostId()).isEqualTo(10L);
        assertThat(received.getCategoryId()).isEqualTo(30L);
        assertThat(received.getName()).isEqualTo("아이폰");
        assertThat(received.getDescription()).isEqualTo("부사 5kg");
        assertThat(received.getBasePrice()).isEqualTo(25000L);
        assertThat(received.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        var image = received.getProductImageList().getFirst();
        assertThat(image.getId()).isEqualTo(100L);
        assertThat(image.getS3key()).isEqualTo("products/a.jpg");
        assertThat(image.getImageSeq()).isZero();
        assertThat(image.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(image.getCreatedAt()).isEqualTo(AT);
        var option = received.getProductOptionList().getFirst();
        assertThat(option.getId()).isEqualTo(200L);
        assertThat(option.getProductTypeId()).isEqualTo(1L);
        assertThat(option.getProductOptionId()).isEqualTo(11L);

        assertThat(fieldPaths(strict.valueToTree(received), ""))
                .containsExactlyInAnyOrderElementsOf(fieldPaths(strict.readTree(json), ""));
    }

    // 배열은 첫 원소 기준으로 펼쳐서 "productList[].productImageList[].imageSeq" 같은 경로 목록을 만든다
    private List<String> fieldPaths(JsonNode node, String prefix) {
        List<String> paths = new ArrayList<>();
        if (node.isArray()) {
            if (!node.isEmpty()) {
                paths.addAll(fieldPaths(node.get(0), prefix + "[]"));
            }
            return paths;
        }
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                String path = prefix.isEmpty() ? name : prefix + "." + name;
                paths.add(path);
                paths.addAll(fieldPaths(node.get(name), path));
            }
        }
        return paths;
    }
}
