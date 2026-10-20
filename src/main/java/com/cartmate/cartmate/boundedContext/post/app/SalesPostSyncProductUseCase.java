package com.cartmate.cartmate.boundedContext.post.app;

import com.cartmate.cartmate.boundedContext.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProductImage;
import com.cartmate.cartmate.boundedContext.post.domain.SalesPostProductOption;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.global.GlobalConfig;
import com.cartmate.cartmate.shared.post.dto.DeleteProductRequest;
import com.cartmate.cartmate.shared.post.dto.ProductSyncUpdateRequest;
import com.cartmate.cartmate.shared.post.dto.productSyncCompletedEvent;
import com.cartmate.cartmate.shared.product.dto.DeleteProductListRequest;
import com.cartmate.cartmate.shared.product.dto.SyncProduct;
import com.cartmate.cartmate.shared.product.dto.SyncProductImage;
import com.cartmate.cartmate.shared.product.dto.SyncProductOption;
import com.cartmate.cartmate.shared.product.dto.SyncProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesPostSyncProductUseCase {
    private final SalesPostRepository salesPostRepository;
    private final SalesPostProductRepository salesPostProductRepository;
    private final SalesPostProductImageRepository salesPostProductImageRepository;
    private final SalesPostProductOptionRepository salesPostProductOptionRepository;

    @Transactional
    public void syncProduct(SyncProductRequest request) {
        if (request.getProductList() == null || request.getProductList().isEmpty()) {
            throw new ServiceException(SalesPostErrorCode.POST_NOT_FOUND);
        }

        SalesPost salesPost = salesPostRepository.findById(request.getProductList().getFirst().getSalesPostId())
                    .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));

        List<SyncProduct> syncProductList = request.getProductList();
        List<Long> successList = new ArrayList<>();
        syncProductList.forEach(product -> {
            SalesPostProduct newProduct = new SalesPostProduct(
                    product.getId(),
                    product.getSellerId(),
                    salesPost,
                    product.getCategoryId(),
                    product.getName(),
                    product.getDescription(),
                    product.getBasePrice(),
                    ProductStatus.ON_SALE,
                    product.getCreateDate(),
                    product.getModifyDate()
            );
            salesPostProductRepository.save(newProduct);

            product.getProductImageList().forEach(image -> {
                SalesPostProductImage newImage = new SalesPostProductImage(
                        image.getId(),
                        image.getSellerId(),
                        newProduct,
                        image.getS3Key(),
                        image.getImageSeq(),
                        image.getStatus(),
                        image.getCreateDate(),
                        image.getModifyDate()
                );
                salesPostProductImageRepository.save(newImage);
            });

            List<SyncProductOption> optionList = product.getProductOptionList();
            if (optionList != null) {
                optionList.forEach(option -> salesPostProductOptionRepository.save(new SalesPostProductOption(
                        option.getId(),
                        newProduct,
                        option.getProductTypeId(),
                        option.getProductOptionId(),
                        option.getCreateDate(),
                        option.getModifyDate()
                )));
            }
            successList.add(product.getId());
        });
        GlobalConfig.getEventPublisher().publish(
                new productSyncCompletedEvent(
                        new ProductSyncUpdateRequest(successList)
                )
        );
    }

    @Transactional
    public void deleteProductByPost(DeleteProductListRequest request) {
        List<Long> productIds = request.getProductList().stream()
                .map(SyncProduct::getId)
                .toList();

        salesPostProductRepository.findByIdIn(productIds)
                .forEach(SalesPostProduct::delete);
    }

    @Transactional
    public void deleteProductByUser(DeleteProductRequest request) {
        salesPostProductRepository.findById(request.getProductId())
                .ifPresent(SalesPostProduct::delete);
    }

    @Transactional
    public void syncUpdatedProduct(SyncProductRequest request) {
//        List<Long> successList = new ArrayList<>();
//
//        for (SyncProduct product : request.getProductList()) {
//            SalesPostProduct target = salesPostProductRepository.findById(product.getId())
//                    .orElseThrow(() -> new ServiceException(SalesPostErrorCode.POST_NOT_FOUND));
//
//            // 1. 상품 정보 갱신 (상태 포함)
//            target.update(product.getCategoryId(), product.getName(), product.getDescription(),
//                    product.getBasePrice(), product.getStatus(), product.getModifyDate());
//
//            // 2. 이미지 비교: 목록에 없는 건 삭제, 있는 건 순서 갱신, 새 건 추가
//            Map<Long, SalesPostProductImage> currentById = salesPostProductImageRepository
//                    .findByProduct(target).stream()
//                    .collect(Collectors.toMap(SalesPostProductImage::getId, Function.identity()));
//
//            Set<Long> syncIds = product.getProductImageList().stream()
//                    .map(SyncProductImage::getId)
//                    .collect(Collectors.toSet());
//
//            currentById.values().stream()
//                    .filter(image -> !syncIds.contains(image.getId()))
//                    .forEach(salesPostProductImageRepository::delete);
//
//            for (SyncProductImage image : product.getProductImageList()) {
//                SalesPostProductImage existing = currentById.get(image.getId());
//                if (existing != null) {
//                    existing.changeSeq(image.getImageSeq());
//                } else {
//                    salesPostProductImageRepository.save(new SalesPostProductImage(
//                            image.getId(), image.getSellerId(), target, image.getS3Key(),
//                            image.getImageSeq(), image.getStatus(),
//                            image.getCreateDate(), image.getModifyDate()));
//                }
//            }
//            successList.add(product.getId());
//        }
//
//        GlobalConfig.getEventPublisher().publish(
//                new productSyncCompletedEvent(new ProductSyncUpdateRequest(successList))
//        );
    }
}
