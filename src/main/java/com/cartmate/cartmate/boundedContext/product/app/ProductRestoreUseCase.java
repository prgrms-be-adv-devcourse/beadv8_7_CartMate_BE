package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.product.domain.ProductOption;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import com.cartmate.cartmate.global.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.product.dto.*;
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

    private void restoreOptions(Product product, List<RestoreProductOption> reqOptionList) {
        // 옵션은 수정 시 삭제되면 id가 사라지므로 값으로 비교한다
        List<RestoreProductOption> remaining = new ArrayList<>(reqOptionList);

        product.getProductOptionList().removeIf(option -> !removeMatching(remaining, option));

        for (RestoreProductOption option : remaining) {
            product.addOption(new ProductOption(product, option.getProductTypeId(), option.getProductOptionId()));
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
