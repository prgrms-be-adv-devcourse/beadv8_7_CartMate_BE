package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.product.dto.UploadedImage;
import com.cartmate.cartmate.boundedContext.product.out.ImageStorage;
import com.cartmate.cartmate.boundedContext.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductImageFileDeleteUseCase {
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ImageStorage imageStorage;

    public void deleteImageByException(List<UploadedImage> uploadedImages) {
        uploadedImages.forEach(image -> imageStorage.delete(image.getS3Key()));
    }
}
