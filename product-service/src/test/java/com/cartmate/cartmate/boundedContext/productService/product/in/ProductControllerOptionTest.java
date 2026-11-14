package com.cartmate.cartmate.boundedContext.productService.product.in;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.boundedContext.productService.product.app.ProductFacade;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CategoryResponse;
import com.cartmate.cartmate.boundedContext.productService.product.dto.ProductOptionTypeResponse;
import com.cartmate.cartmate.boundedContext.productService.product.dto.ProductOptionValueResponse;
import com.cartmate.cartmate.global.exception.GlobalExceptionHandler;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.rsData.RsData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 옵션 조회 API(ProductController): 주소와 JSON 모양. 프론트가 typeId · typeName · options[].optionId · name으로 선택 목록을 그린다.
 * 고정 경로 /options가 기존 /{postId}(판매글의 상품 조회)와 겹치지 않는지도 본다.
 */
class ProductControllerOptionTest {

    private static final ProductOptionTypeResponse SHOE_SIZE = new ProductOptionTypeResponse(3L, "신발 사이즈",
            List.of(new ProductOptionValueResponse(18L, "220"), new ProductOptionValueResponse(19L, "225")));

    private final ProductFacade productFacade = mock(ProductFacade.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(productFacade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/products/categories: 카테고리 목록(categoryId · name)을 돌려준다")
    void getCategories() throws Exception {
        given(productFacade.findCategories()).willReturn(List.of(
                new CategoryResponse(1L, "유니폼 / 의류"), new CategoryResponse(3L, "신발 / 보호구")));

        mvc.perform(get("/api/products/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data[0].categoryId").value(1))
                .andExpect(jsonPath("$.data[0].name").value("유니폼 / 의류"))
                .andExpect(jsonPath("$.data[1].categoryId").value(3));
    }

    @Test
    @DisplayName("GET /api/products/options: 옵션 종류 목록과 종류별 상품옵션을 돌려준다")
    void getAll() throws Exception {
        given(productFacade.findProductOptions()).willReturn(List.of(
                new ProductOptionTypeResponse(2L, "색상", List.of(new ProductOptionValueResponse(8L, "블랙"))),
                SHOE_SIZE));

        mvc.perform(get("/api/products/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data[0].typeId").value(2))
                .andExpect(jsonPath("$.data[0].typeName").value("색상"))
                .andExpect(jsonPath("$.data[0].options[0].optionId").value(8))
                .andExpect(jsonPath("$.data[0].options[0].name").value("블랙"))
                .andExpect(jsonPath("$.data[1].options.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/products/options/{typeId}: 옵션 종류 하나의 상품옵션을 돌려준다")
    void getByType() throws Exception {
        given(productFacade.findProductOptions(3L)).willReturn(SHOE_SIZE);

        mvc.perform(get("/api/products/options/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.typeId").value(3))
                .andExpect(jsonPath("$.data.typeName").value("신발 사이즈"))
                .andExpect(jsonPath("$.data.options[1].optionId").value(19))
                .andExpect(jsonPath("$.data.options[1].name").value("225"));
    }

    @Test
    @DisplayName("GET /api/products/options/{typeId}: 없는 옵션 종류면 404")
    void getByUnknownType() throws Exception {
        given(productFacade.findProductOptions(999L))
                .willThrow(new ServiceException(ProductErrorCode.PRODUCT_OPTION_TYPE_NOT_FOUND));

        mvc.perform(get("/api/products/options/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("기존 GET /api/products/{postId}는 그대로 판매글의 상품 조회로 간다")
    void postIdRouteStillWorks() throws Exception {
        given(productFacade.selectProductsByPost(10L)).willReturn(new RsData<>("200-1", "상품 조회에 성공했습니다.", List.of()));

        mvc.perform(get("/api/products/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"));
        then(productFacade).should().selectProductsByPost(10L);
    }
}
