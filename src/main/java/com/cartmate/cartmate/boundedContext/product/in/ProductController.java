package com.cartmate.cartmate.boundedContext.product.in;

import com.cartmate.cartmate.boundedContext.post.dto.SelectProductResponse;
import com.cartmate.cartmate.boundedContext.product.app.ProductFacade;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.product.dto.CreateProductRequest;
import com.cartmate.cartmate.shared.product.dto.RestoreProductsRequest;
import com.cartmate.cartmate.shared.product.dto.UpdateProductsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.cartmate.cartmate.shared.product.dto.ProductStatusChangeRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {
    private final ProductFacade productFacade;

    @PostMapping
    public ResponseEntity<Void> registerProducts(@RequestPart("request") CreateProductRequest request,
                                                @RequestPart(value = "images") List<MultipartFile> images) {
        productFacade.registerProduct(request, images);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deleteProductByPost(@PathVariable Long postId) {
        productFacade.deleteProductByPost(postId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @DeleteMapping("/sellers/{sellerId}/products/{productId}")
    public ResponseEntity<Void> deleteProducts(
            @PathVariable Long sellerId,
            @PathVariable Long productId) {
        productFacade.deleteProductByUser(productId, sellerId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    @PatchMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> updateProducts(
            @RequestPart("request") UpdateProductsRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        productFacade.updateProducts(request, images);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{postId}/restore")
    public ResponseEntity<Void> restoreProducts(
            @PathVariable Long postId,
            @RequestBody RestoreProductsRequest request
    ) {
        productFacade.restoreProducts(postId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{postId}")
    public ResponseEntity<RsData<List<SelectProductResponse>>> selectProductsByPost(@PathVariable Long postId) {
        return ResponseEntity.ok(productFacade.selectProductsByPost(postId));
    }

    @PatchMapping("/{postId}/status")
    @Operation(operationId = "changeProductStatusByPost", summary = "판매글 단위 상품 판매 상태 변경 (내부 호출)")
    public ResponseEntity<Void> changeStatusByPost(@PathVariable Long postId,
                                                   @Valid @RequestBody ProductStatusChangeRequest request) {
        productFacade.changeStatusByPost(postId, request.status());
        return ResponseEntity.noContent().build();
    }
}
