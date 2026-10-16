package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.dto.upload.UploadedImage;
import com.cartmate.cartmate.global.code.SuccessCode;
import com.cartmate.cartmate.global.response.ResponseData;
import com.cartmate.cartmate.shared.product.dto.create.CreateProduct;
import com.cartmate.cartmate.shared.product.dto.create.CreateProductRequest;
import com.cartmate.cartmate.shared.product.dto.create.CreateProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductFacade {
    private final ProductCreateDraftUseCase productCreateDraftUseCase;
    private final ProductSaveUseCase productSaveUseCase;
    private final ProductImageSaveUseCase productImageSaveUseCase;
    private final ProductImageFileUploadUseCase productImageFileUploadUseCase;
    private final ProductImageFileDeleteUseCase productImageFileDeleteUseCase;
    private final ProductSupport productSupport;
    private final TransactionTemplate transactionTemplate;

    public ResponseData<CreateProductResponse> registerProduct(CreateProductRequest request) {
        productSupport.validateProductCount(request.getProductList());
        productSupport.validateProductFormat(request.getProductList());

        List<UploadedImage> uploadedImageList = productImageFileUploadUseCase.uploadAll(request);
        try {
            List<CreateProduct> productIdList = transactionTemplate.execute(status -> {
                List<CreateProduct> draftProductList = productCreateDraftUseCase.createProductDraft(request);
                productSaveUseCase.publishProduct(draftProductList);
                productImageSaveUseCase.saveProductImage(draftProductList, uploadedImageList);
                return draftProductList;
            });

            return new ResponseData<>(
                    SuccessCode.PRODUCT_CREATED,
                    new CreateProductResponse(
                            request.getPostId(),
                            productIdList
                    )
            );
        } catch (RuntimeException e) {
//            deleteQuietly(uploaded);
            throw e;
        }
    }
}
