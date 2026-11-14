package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.product.domain.Inventory;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreInventory;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProduct;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.productService.product.dto.ProductUpdateResult;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreProductsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

            // 재고 도입 전 스냅샷은 재고 목록이 없다(null) → 재고는 건드리지 않는다
            if (productReq.getInventoryList() != null) {
                restoreInventories(product, productReq.getInventoryList());
            }

            // 수정 중(UPDATING)이면 스냅샷의 수정 전 상태로 되돌려 다시 구매할 수 있게 한다
            product.finishUpdating(productReq.getStatus());

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

    // 스냅샷과 같은 재고 구성으로 되돌린다. 재고는 행을 지우지 않으므로 ID로 짝을 짓는다
    // - 스냅샷에 있는 재고: 스냅샷의 추가금액 · 재고 수량으로 되살린다(수정 때 지워졌어도 같은 ID, 선택된 옵션도 함께)
    // - 스냅샷에 없는 재고(실패한 수정에서 새로 추가된 것): 선택된 옵션과 함께 삭제 상태로
    // ※ 재고 차감(주문 연동)이 붙으면, 스냅샷 이후 주문으로 바뀐 재고 수량을 덮어쓰지 않도록 다시 봐야 한다
    private void restoreInventories(Product product, List<RestoreInventory> reqInventoryList) {
        Map<Long, RestoreInventory> snapshotById = new HashMap<>(reqInventoryList.stream()
                .collect(Collectors.toMap(RestoreInventory::getId, Function.identity())));

        for (Inventory inventory : product.getInventoryList()) {
            RestoreInventory snapshot = snapshotById.remove(inventory.getId());
            if (snapshot != null) {
                inventory.restore(snapshot.getAdditionalPrice(), snapshot.getStockQuantity());
            } else if (inventory.isActive()) {
                inventory.delete();
            }
        }

        // 스냅샷에 있는데 상품에 없는 재고 ID는 이 상품의 재고가 아니다
        if (!snapshotById.isEmpty()) {
            throw new ServiceException(ProductErrorCode.PRODUCT_INVENTORY_NOT_FOUND);
        }
    }
}
