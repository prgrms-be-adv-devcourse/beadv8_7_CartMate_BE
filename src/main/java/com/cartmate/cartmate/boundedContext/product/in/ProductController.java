package com.cartmate.cartmate.boundedContext.product.in;

import com.cartmate.cartmate.boundedContext.product.app.ProductFacade;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.product.dto.CreateProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {
    private final ProductFacade productFacade;

    @PostMapping("/register")
    public ResponseEntity<Void> registerProduct(@RequestPart("request") CreateProductRequest request,
                                                @RequestPart(value = "images") List<MultipartFile> images) {
        productFacade.registerProduct(request, images);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deleteProductByPost(@PathVariable Long postId) {
        productFacade.deleteProductByPost(postId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long productId,
            @RequestParam Long sellerId) {
//        productFacade.deleteProductByUser(productId, sellerId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

}
