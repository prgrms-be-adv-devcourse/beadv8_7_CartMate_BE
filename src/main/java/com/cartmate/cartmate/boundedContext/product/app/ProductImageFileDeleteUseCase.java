package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.dto.UploadedImage;
import com.cartmate.cartmate.boundedContext.product.out.ImageStorage;
import com.cartmate.cartmate.global.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductImageFileDeleteUseCase {
    private final ImageStorage imageStorage;

    public void deleteImageByException(List<UploadedImage> uploadedImages) {
        uploadedImages.forEach(image -> imageStorage.delete(image.getS3Key()));
    }

    public void deleteByKeys(List<String> keys) {
        for (String key : keys) {
            try {
                imageStorage.delete(key);
            } catch (RuntimeException e) {
                throw new ServiceException(ProductErrorCode.PRODUCT_IMAGE_DELETE_FAILED);
            }
        }
    }
}
