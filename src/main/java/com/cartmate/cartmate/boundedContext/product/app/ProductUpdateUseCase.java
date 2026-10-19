package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.product.dto.UploadedImage;
import com.cartmate.cartmate.boundedContext.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import com.cartmate.cartmate.global.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.product.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductUpdateUseCase {
    private final ProductRepository productRepository;

    public ProductUpdateResult updateProduct(UpdateProductsRequest request, List<UploadedImage> uploadedImageList) {
        // 업로드 결과: "상품인덱스:이미지순서" → s3Key
        Map<String, String> uploadedKeyMap = uploadedImageList.stream()
                .collect(Collectors.toMap(
                        u -> u.getProductIndex() + ":" + u.getImageSeq(),
                        UploadedImage::getS3Key));

        List<Product> updatedProducts = new ArrayList<>();
        List<String> deletedKeys = new ArrayList<>();

        List<UpdateProduct> productList = request.getProductList();
        for (int i = 0; i < productList.size(); i++) {
            UpdateProduct productReq = productList.get(i);

            Product product = productRepository.findById(productReq.getId())
                    .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_NOT_FOUND));

            // 다른 판매글의 상품을 수정하지 못하도록
            if (!product.getSalesPostId().equals(request.getId())) {
                throw new ServiceException(ProductErrorCode.PRODUCT_ACCESS_DENIED);
            }

            product.update(productReq.getCategoryId(), productReq.getName(), productReq.getDescription(),
                    productReq.getBasePrice(), productReq.getStatus());
            updatedProducts.add(product);

            // 이미지 변경 없음
            if (productReq.getProductImageList() == null) {
                continue;
            }

            // 현재 살아 있는 이미지
            Map<Long, ProductImage> currentById = product.getProductImageList().stream()
                    .filter(image -> image.getStatus() != ProductStatus.DELETED)
                    .collect(Collectors.toMap(ProductImage::getId, Function.identity()));

            Set<Long> keepIds = productReq.getProductImageList().stream()
                    .map(UpdateProductImage::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // 1. 요청에 없는 기존 이미지 소프트 삭제
            currentById.values().stream()
                    .filter(image -> !keepIds.contains(image.getId()))
                    .forEach(image -> {
                        deletedKeys.add(image.getS3Key());
                        image.delete();
                    });

            // 2. 유지 이미지는 순서 갱신, 새 이미지는 추가
            for (UpdateProductImage imageReq : productReq.getProductImageList()) {
                if (imageReq.getId() == null) {
                    String key = uploadedKeyMap.get(i + ":" + imageReq.getImageSeq());
                    product.addImage(new ProductImage(product.getSellerId(), product, key,
                            imageReq.getImageSeq(), ProductStatus.ON_SALE, false));
                } else {
                    ProductImage existing = currentById.get(imageReq.getId());
                    if (existing == null) { // 다른 상품의 이미지 id를 보낸 경우
                        throw new ServiceException(ProductErrorCode.PRODUCT_IMAGE_NOT_FOUND);
                    }
                    existing.changeSeq(imageReq.getImageSeq());
                }
            }
        }

        // 새 이미지 INSERT → id 채워짐
        productRepository.flush();

        List<SyncProduct> syncProductList = updatedProducts.stream()
                .map(Product::toSyncProduct)
                .toList();

        return new ProductUpdateResult(syncProductList, deletedKeys);
    }
}
