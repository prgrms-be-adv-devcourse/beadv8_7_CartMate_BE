package com.cartmate.cartmate.boundedContext.productService.product.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 상품 등록 요청의 post → product 계약.
 * post는 자기 dto(post.dto.CreateProductRequest)를 JSON으로 보내고, product는 그 JSON을 자기 dto로 읽는다.
 * 두 dto는 따로 관리되므로 필드 이름이 어긋나면 값이 조용히 빠진다. 그걸 여기서 잡는다.
 */
class CreateProductRequestContractTest {

    // 모르는 필드가 오면 실패하게 해서, post에만 있는 필드가 생기면 바로 드러나게 한다
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    @DisplayName("post가 보낸 등록 요청의 값이 product dto로 빠짐없이 옮겨진다")
    void postRequestIsReadAsProductRequest() {
        String json = jsonMapper.writeValueAsString(postRequest());

        CreateProductRequest received = jsonMapper.readValue(json, CreateProductRequest.class);

        assertThat(received.getPostId()).isEqualTo(10L);
        assertThat(received.getProductList()).hasSize(1);

        CreateProduct product = received.getProductList().get(0);
        assertThat(product.getId()).isNull();
        assertThat(product.getSellerId()).isEqualTo(20L);
        assertThat(product.getSalesPostId()).isEqualTo(10L);
        assertThat(product.getCategoryId()).isEqualTo(30L);
        assertThat(product.getName()).isEqualTo("사과");
        assertThat(product.getDescription()).isEqualTo("부사 5kg");
        assertThat(product.getBasePrice()).isEqualTo(25000L);

        assertThat(product.getProductImageList()).hasSize(2);
        CreateProductImage image = product.getProductImageList().get(1);
        assertThat(image.getId()).isNull();
        assertThat(image.getSellerId()).isEqualTo(20L);
        assertThat(image.getProductId()).isNull();
        assertThat(image.getS3Key()).isEqualTo("products/apple-2.jpg");
        assertThat(image.getImageSeq()).isEqualTo(2);

        assertThat(product.getProductOptionList())
                .extracting(CreateProductOption::getProductTypeId, CreateProductOption::getProductOptionId)
                .containsExactly(tuple(1L, 100L), tuple(2L, 200L));
    }

    @Test
    @DisplayName("사진 파일(file)은 JSON에 담기지 않고, product 쪽에서는 비어 있다")
    void imageFileIsNotSentAsJson() {
        com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductRequest request = postRequest();
        request.getProductList().get(0).getProductImageList().get(0)
                .assignFile(new MockMultipartFile("images", "apple-1.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        String json = jsonMapper.writeValueAsString(request);
        CreateProductRequest received = jsonMapper.readValue(json, CreateProductRequest.class);

        assertThat(json).doesNotContain("\"file\"");
        assertThat(received.getProductList().get(0).getProductImageList().get(0).getFile()).isNull();
    }

    @Test
    @DisplayName("post dto와 product dto는 JSON 필드 구성이 같다")
    void bothSidesHaveSameJsonFields() {
        JsonNode sent = jsonMapper.valueToTree(postRequest());
        JsonNode read = jsonMapper.valueToTree(jsonMapper.readValue(sent.toString(), CreateProductRequest.class));

        assertThat(fieldPaths(read, "")).containsExactlyInAnyOrderElementsOf(fieldPaths(sent, ""));
    }

    private com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductRequest postRequest() {
        var images = new ArrayList<>(List.of(
                new com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductImage(
                        null, 20L, null, "products/apple-1.jpg", 1, null),
                new com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductImage(
                        null, 20L, null, "products/apple-2.jpg", 2, null)
        ));
        var options = new ArrayList<>(List.of(
                new com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductOption(1L, 100L),
                new com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductOption(2L, 200L)
        ));
        var product = new com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProduct(
                null, 20L, 10L, 30L, "사과", "부사 5kg", 25000L, images, options);

        return new com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductRequest(
                10L, new ArrayList<>(List.of(product)));
    }

    // 배열은 첫 원소 기준으로 펼쳐서 "productList[].productImageList[].s3Key" 같은 경로 목록을 만든다
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
