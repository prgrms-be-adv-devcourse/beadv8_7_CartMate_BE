package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.dto.UploadedImage;
import com.cartmate.cartmate.shared.post.dto.SalesPostRollbackRequest;
import com.cartmate.cartmate.shared.product.dto.CreateProduct;
import com.cartmate.cartmate.shared.product.dto.CreateProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductFacade {
    private final ProductCreateUseCase productCreateUseCase;
    private final ProductImageFileUploadUseCase productImageFileUploadUseCase;
    private final ProductImageFileDeleteUseCase productImageFileDeleteUseCase;
    private final ProductDeleteUseCase productDeleteUseCase;
    private final ProductSupport productSupport;
    private final TransactionTemplate transactionTemplate;

    public void registerProduct(CreateProductRequest request, List<MultipartFile> images) {
        productSupport.includeFileData(request, images);
        productSupport.validateProductCount(request.getProductList());
        productSupport.validateProductFormat(request.getProductList());

        List<UploadedImage> uploadedImageList = productImageFileUploadUseCase.uploadAll(request);
        try {
            List<CreateProduct> productIdList = transactionTemplate.execute(status -> {
                return productCreateUseCase.saveProduct(request, uploadedImageList);
            });
        } catch (RuntimeException e) {
            productImageFileDeleteUseCase.deleteImageByException(uploadedImageList);
            throw e;
        }
    }

    @Transactional
    public void deleteSalesPostByUser(Long id) {
        productDeleteUseCase.deleteProductByUser(id);
    }

    public void deleteProductByEvent(SalesPostRollbackRequest salesPostRollbackRequest) {
        productImageFileDeleteUseCase.deleteImageByEvent(salesPostRollbackRequest.getId());
        productDeleteUseCase.deleteProductByEvent(salesPostRollbackRequest.getId());
    }
}
