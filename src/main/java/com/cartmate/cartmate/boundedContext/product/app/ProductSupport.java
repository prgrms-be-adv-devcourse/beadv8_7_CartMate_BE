package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.ImageTypePolicy;
import com.cartmate.cartmate.boundedContext.product.domain.ProductCountPolicy;
import com.cartmate.cartmate.boundedContext.product.dto.ImageUploadTask;
import com.cartmate.cartmate.global.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.product.dto.CreateProduct;
import com.cartmate.cartmate.shared.product.dto.CreateProductImage;
import com.cartmate.cartmate.shared.product.dto.CreateProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductSupport {
    private final ImageTypePolicy imageTypePolicy;
    private final ProductCountPolicy productCountPolicy;

    public void validateProductCount(List<CreateProduct> productList) {
        if (!productCountPolicy.isSafeProductCount(productList)) {
            throw new ServiceException(ProductErrorCode.PRODUCT_COUNT_INVALID);
        }

        if (!productCountPolicy.isSafeProductImageCount(productList)) {
            throw new ServiceException(ProductErrorCode.PRODUCT_IMAGE_COUNT_EXCEEDED);
        }
    }

    public void validateProductFormat(List<CreateProduct> productList) {
        productList.forEach(product ->
                product.getProductImageList().forEach(image ->
                        imageTypePolicy.detect(image.getFile())));
    }

    public List<ImageUploadTask> toTasks(List<CreateProduct> productList) {
        List<ImageUploadTask> tasks = new ArrayList<>();
        for (int productIndex = 0; productIndex < productList.size(); productIndex++) {
            List<CreateProductImage> images = productList.get(productIndex).getProductImageList();
            for (int imageSeq = 0; imageSeq < images.size(); imageSeq++) {
                tasks.add(new ImageUploadTask(productIndex, imageSeq, images.get(imageSeq).getFile()));
            }
        }
        return tasks;
    }

    public void includeFileData(CreateProductRequest request, List<MultipartFile> images) {
        int cursor = 0;
        for (CreateProduct product : request.getProductList()) {
            for (CreateProductImage image : product.getProductImageList()) {
                image.assignFile(images.get(cursor++));
            }
        }
        if (cursor != images.size()) {
            throw new ServiceException(ProductErrorCode.PRODUCT_CREATE_INVALID);
        }
    }
}
