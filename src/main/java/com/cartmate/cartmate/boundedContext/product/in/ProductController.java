package com.cartmate.cartmate.boundedContext.product.in;

import com.cartmate.cartmate.boundedContext.product.app.ProductFacade;
import com.cartmate.cartmate.shared.product.dto.CreateProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {
    private final ProductFacade productFacade;

    @PostMapping("/product")
    public ResponseEntity<Void> registerProduct(@RequestPart("request") CreateProductRequest request,
                                                @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        productFacade.registerProduct(request, images);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

}
