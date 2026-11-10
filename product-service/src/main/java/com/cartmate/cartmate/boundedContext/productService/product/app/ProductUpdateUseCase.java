package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.ProductUpdateResult;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProductsRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UploadedImage;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductUpdateUseCase {
    private final ProductRepository productRepository;

    @Transactional
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
                    productReq.getBasePrice());
            updatedProducts.add(product);

            // 이미지 변경이 있을 때만
            if (productReq.getProductImageList() != null) {
                updateImages(product, productReq.getProductImageList(), uploadedKeyMap, i, deletedKeys);
            }

            // 옵션 변경이 있을 때만
            if (productReq.getProductOptionList() != null) {
                updateOptions(product, productReq.getProductOptionList());
            }
        }

        productRepository.flush();

        List<SyncProduct> syncProductList = updatedProducts.stream()
                .map(Product::toSyncProduct)
                .toList();

        return new ProductUpdateResult(syncProductList, deletedKeys);
    }

    private void updateImages(Product product, List<UpdateProductImage> reqImageList,
                              Map<String, String> uploadedKeyMap, int productIndex,
                              List<String> deletedKeys) {
        // DB에 저장되어 있던 삭제되지 않은 이미지 List
        Map<Long, ProductImage> storedImageList = product.getProductImageList().stream()
                .filter(image -> image.getStatus() != ProductStatus.DELETED)
                .collect(Collectors.toMap(ProductImage::getId, Function.identity()));

        // DB에서 삭제되지 않을 이미지의 Id List
        Set<Long> keepImageIds = reqImageList.stream()
                .map(UpdateProductImage::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 요청(reqImageList)에 포함되지 않은 삭제 대상이므로 List는 삭제
        storedImageList.values().stream()
                .filter(image -> !keepImageIds.contains(image.getId()))
                .forEach(image -> {
                    deletedKeys.add(image.getS3Key());
                    image.delete();
                });

        // 2. 유지 이미지는 순서 갱신, 새 이미지는 추가
        for (UpdateProductImage imageReq : reqImageList) {
            if (imageReq.getId() == null) { // id 없는 경우 : 새 이미지
                String key = uploadedKeyMap.get(productIndex + ":" + imageReq.getImageSeq());
                product.addImage(new ProductImage(product.getSellerId(), product, key,
                        imageReq.getImageSeq(), ProductStatus.ACTIVE));
            } else {
                ProductImage existing = storedImageList.get(imageReq.getId());
                if (existing == null) { // 저장되어 있던 이미지가 아닌 경우
                    throw new ServiceException(ProductErrorCode.PRODUCT_IMAGE_NOT_STORED);
                }
                existing.changeSeq(imageReq.getImageSeq()); // 이미지 교체
            }
        }
    }

    private void updateOptions(Product product, List<UpdateProductOption> reqOptionList) {
        // 이미 삭제된 옵션은 수정 대상이 아니다(요청에 그 ID가 오면 PRODUCT_OPTION_NOT_FOUND)
        Map<Long, ProductOption> storedIdMap = product.getProductOptionList().stream()
                .filter(ProductOption::isActive)
                .collect(Collectors.toMap(ProductOption::getId, Function.identity()));

        Set<Long> keepOptionIds = reqOptionList.stream()
                .map(UpdateProductOption::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 요청에 없는 기존 옵션은 삭제 상태로 바꾼다(행을 지우지 않아 옵션 ID가 남는다)
        storedIdMap.values().stream()
                .filter(o -> !keepOptionIds.contains(o.getId()))
                .forEach(ProductOption::delete);

        for (UpdateProductOption option : reqOptionList) {
            if (option.getId() == null) {
                ProductOption newOption = new ProductOption(product, option.getProductTypeId(), option.getProductOptionId(),
                        ProductStatus.ACTIVE);
                product.addOption(newOption);
            } else {
                ProductOption existing = storedIdMap.get(option.getId());
                if (existing == null) {
                    throw new ServiceException(ProductErrorCode.PRODUCT_OPTION_NOT_FOUND);
                }
                existing.update(option.getProductTypeId(), option.getProductOptionId());
            }
        }
    }
}
