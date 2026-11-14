package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SyncProduct;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductStatus;
import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.boundedContext.productService.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SyncProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SyncProductOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesPostSyncProductUseCase {
    private final SalesPostRepository salesPostRepository;
    private final SalesPostProductRepository salesPostProductRepository;
    private final SalesPostProductImageRepository salesPostProductImageRepository;
    private final SalesPostProductOptionRepository salesPostProductOptionRepository;

    // product의 등록 · 수정 · 복구 응답으로 복제본을 저장한다(같은 ID면 덮어씀).
    // 부르는 쪽의 마무리 트랜잭션에 합쳐져서, 실패하면 그 작업(등록 · 수정 · 복구)도 실패한다
    @Transactional
    public void syncProduct(List<SyncProduct> syncList) {
        if (syncList == null || syncList.isEmpty()) {
            throw new ServiceException(ProductErrorCode.PRODUCT_SYNC_DATA_NOT_FOUND);
        }

        SalesPost salesPost = salesPostRepository.findById(syncList.getFirst().getSalesPostId())
                    .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));

        syncList.forEach(product -> {
            SalesPostProduct newProduct = new SalesPostProduct(
                    product.getId(),
                    product.getSellerId(),
                    salesPost,
                    product.getCategoryId(),
                    product.getName(),
                    product.getDescription(),
                    product.getBasePrice(),
                    SalesPostProductStatus.ON_SALE,
                    product.getCreateDate(),
                    product.getModifyDate()
            );
            salesPostProductRepository.save(newProduct);

            List<SyncProductImage> imageList = product.getProductImageList();
            if (imageList != null) {
                imageList.forEach(image -> {
                    SalesPostProductImage newImage = SalesPostProductImage.from(image, newProduct);
                    salesPostProductImageRepository.save(newImage);
                });
            }

            List<SyncProductOption> optionList = product.getProductOptionList();
            if (optionList != null) {
                optionList.forEach(option -> {
                    SalesPostProductOption newOption = SalesPostProductOption.from(option, newProduct);
                    salesPostProductOptionRepository.save(newOption);
                });
            }
        });
    }

    // 판매글 삭제로 product에서 지워진 상품의 복제본을 지운다(소프트 삭제라 다시 와도 결과가 같다)
    @Transactional
    public void deleteProductByPost(List<Long> productIds) {
        salesPostProductRepository.findByIdIn(productIds)
                .forEach(SalesPostProduct::delete);
    }

    // 판매자가 지운 상품 하나의 복제본을 지운다(소프트 삭제라 다시 와도 결과가 같다)
    @Transactional
    public void deleteProductByUser(Long productId) {
        salesPostProductRepository.findById(productId)
                .ifPresent(SalesPostProduct::delete);
    }

}
