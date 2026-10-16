package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.dto.upload.UploadedImage;
import com.cartmate.cartmate.boundedContext.product.out.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductImageFileDeleteUseCase {
    private final ImageStorage imageStorage;

    public void deleteAll(List<UploadedImage> uploadedImages) {
        uploadedImages.forEach(image -> imageStorage.delete(image.getS3Key()));
    }
}
