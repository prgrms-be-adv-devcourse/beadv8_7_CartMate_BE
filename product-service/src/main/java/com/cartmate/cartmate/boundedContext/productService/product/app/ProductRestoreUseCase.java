package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProduct;
import com.cartmate.cartmate.shared.product.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.productService.product.dto.ProductUpdateResult;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreProductsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductRestoreUseCase {
    private final ProductRepository productRepository;

    // 스냅샷 상태로 되돌린다. 같은 요청을 여러 번 보내도 결과가 같다.
    @Transactional
    public ProductUpdateResult restoreProduct(Long postId, RestoreProductsRequest request) {
        List<Product> restoredProducts = new ArrayList<>();
        List<String> deletedKeys = new ArrayList<>();

        for (RestoreProduct productReq : request.getProductList()) {
            Product product = productRepository.findById(productReq.getId())
                    .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_NOT_FOUND));

            if (!product.getSalesPostId().equals(postId)) {
                throw new ServiceException(ProductErrorCode.PRODUCT_ACCESS_DENIED);
            }

            product.update(productReq.getCategoryId(), productReq.getName(), productReq.getDescription(),
                    productReq.getBasePrice());

            if (productReq.getProductImageList() != null) {
                restoreImages(product, productReq.getProductImageList(), deletedKeys);
            }

            if (productReq.getProductOptionList() != null) {
                restoreOptions(product, productReq.getProductOptionList());
            }

            restoredProducts.add(product);
        }

        productRepository.flush();

        List<SyncProduct> syncProductList = restoredProducts.stream()
                .map(Product::toSyncProduct)
                .toList();

        return new ProductUpdateResult(syncProductList, deletedKeys);
    }

    private void restoreImages(Product product, List<RestoreProductImage> reqImageList, List<String> deletedKeys) {
        // 스냅샷에 있던 이미지 id → 순서
        Map<Long, Integer> snapshotSeqMap = reqImageList.stream()
                .collect(Collectors.toMap(RestoreProductImage::getId, RestoreProductImage::getImageSeq));

        for (ProductImage image : product.getProductImageList()) {
            Integer seq = snapshotSeqMap.get(image.getId());
            if (seq != null) {
                image.restore(seq);
            } else if (image.getStatus() != ProductStatus.DELETED) { // 실패한 수정에서 새로 추가된 이미지
                deletedKeys.add(image.getS3Key());
                image.delete();
            }
        }
    }

    // 스냅샷과 같은 옵션 구성으로 되돌린다. 값(종류 · 옵션)으로 짝을 지어
    // 1) 살아 있는 옵션 중 짝이 있으면 그대로 두고, 없으면 삭제 상태로
    // 2) 남은 스냅샷 옵션은 수정 때 삭제된 같은 값의 옵션을 되살리고(같은 ID), 그래도 없을 때만 새로 만든다
    private void restoreOptions(Product product, List<RestoreProductOption> reqOptionList) {
        List<RestoreProductOption> remaining = new ArrayList<>(reqOptionList);

        product.getProductOptionList().stream()
                .filter(ProductOption::isActive)
                .filter(option -> !removeMatching(remaining, option))
                .forEach(ProductOption::delete);

        product.getProductOptionList().stream()
                .filter(option -> !option.isActive())
                .filter(option -> removeMatching(remaining, option))
                .forEach(ProductOption::restore);

        for (RestoreProductOption option : remaining) {
            product.addOption(new ProductOption(product, option.getProductTypeId(), option.getProductOptionId(),
                    ProductStatus.ACTIVE));
        }
    }

    // 스냅샷에 같은 값의 옵션이 남아 있으면 하나만 짝지어 제거하고 true를 반환한다
    private boolean removeMatching(List<RestoreProductOption> remaining, ProductOption option) {
        for (int i = 0; i < remaining.size(); i++) {
            RestoreProductOption req = remaining.get(i);
            if (Objects.equals(req.getProductTypeId(), option.getProductTypeId())
                    && Objects.equals(req.getProductOptionId(), option.getProductOptionId())) {
                remaining.remove(i);
                return true;
            }
        }
        return false;
    }
}
