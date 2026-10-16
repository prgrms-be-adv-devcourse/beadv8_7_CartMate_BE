package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.dto.upload.UploadedImage;
import com.cartmate.cartmate.shared.product.dto.create.CreateProduct;
import com.cartmate.cartmate.shared.product.dto.create.CreateProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductFacade {
    private final ProductCreateUseCase productCreateUseCase;
    private final ProductImageSaveUseCase productImageSaveUseCase;
    private final ProductImageFileUploadUseCase productImageFileUploadUseCase;
    private final ProductImageFileDeleteUseCase productImageFileDeleteUseCase;
    private final ProductSupport productSupport;
    private final TransactionTemplate transactionTemplate;

    public void registerProduct(CreateProductRequest request) {
        productSupport.validateProductCount(request.getProductList());
        productSupport.validateProductFormat(request.getProductList());

        List<UploadedImage> uploadedImageList = productImageFileUploadUseCase.uploadAll(request);
        try {
            List<CreateProduct> productIdList = transactionTemplate.execute(status -> {
                List<CreateProduct> productList = productCreateUseCase.saveProduct(request);
                productImageSaveUseCase.saveProductImage(productList, uploadedImageList);
                return productList;
            });
        } catch (RuntimeException e) {
//            deleteQuietly(uploaded);
            throw e;
        }
    }
}
