package com.cartmate.cartmate.boundedContext.product.in;

import com.cartmate.cartmate.boundedContext.product.app.ProductFacade;
import com.cartmate.cartmate.global.code.SuccessCode;
import com.cartmate.cartmate.global.response.ResponseData;
import com.cartmate.cartmate.shared.product.dto.create.CreateProductRequest;
import com.cartmate.cartmate.shared.product.dto.create.CreateProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {
    private final ProductFacade productFacade;

    @PostMapping("/product")
    public ResponseEntity<ResponseData<CreateProductResponse>> registerProduct(CreateProductRequest request) {
        ResponseData<CreateProductResponse> response = productFacade.registerProduct(request);
        return ResponseEntity
                .status(SuccessCode.PRODUCT_CREATED.getStatus())
                .body(response);
    }

}
