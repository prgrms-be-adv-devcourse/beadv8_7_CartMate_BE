package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.dto.UploadedImage;
import com.cartmate.cartmate.global.global.GlobalConfig;
import com.cartmate.cartmate.shared.post.dto.SalesPostRollbackRequest;
import com.cartmate.cartmate.shared.product.dto.CreateProductRequest;
import com.cartmate.cartmate.shared.product.dto.RollbackProductRequest;
import com.cartmate.cartmate.shared.product.dto.SyncProduct;
import com.cartmate.cartmate.shared.product.dto.SyncProductRequest;
import com.cartmate.cartmate.shared.product.event.ProductCreatedEvent;
import com.cartmate.cartmate.shared.product.event.ProductDeletedEvent;
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
            List<SyncProduct> syncProductList = transactionTemplate.execute(status -> {
                List<SyncProduct> productList = productCreateUseCase.saveProduct(request, uploadedImageList);

                GlobalConfig.getEventPublisher().publish(
                        new ProductCreatedEvent(
                                new SyncProductRequest(productList)
                        )
                );

                return productList;
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

    public void deleteProductByServer(SalesPostRollbackRequest SalesPostRollbackRequest) {
        productImageFileDeleteUseCase.deleteImageByServer(SalesPostRollbackRequest.getId());
        productDeleteUseCase.deleteProductByServer(SalesPostRollbackRequest.getId());
    }
}
