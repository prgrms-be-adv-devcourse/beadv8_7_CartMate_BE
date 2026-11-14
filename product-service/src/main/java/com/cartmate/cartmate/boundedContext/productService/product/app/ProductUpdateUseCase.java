package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.product.domain.Inventory;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CompleteProductUpdateRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.ProductUpdateResult;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProductsRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateInventory;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UploadedImage;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionValueRepository;
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
    private final ProductOptionValueRepository productOptionValueRepository;

    // 판매글 수정 끝: 수정 중(UPDATING)인 상품을 post가 보낸 수정 전 상태로 되돌린다. 다시 불러도 결과가 같다(멱등)
    @Transactional
    public void completeUpdate(Long postId, CompleteProductUpdateRequest request) {
        for (CompleteProductUpdateRequest.Item item : request.getProductList()) {
            Product product = productRepository.findById(item.getId())
                    .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_NOT_FOUND));
            if (!product.getSalesPostId().equals(postId)) {
                throw new ServiceException(ProductErrorCode.PRODUCT_ACCESS_DENIED);
            }
            product.finishUpdating(item.getStatus());
        }
    }

    @Transactional
    public ProductUpdateResult updateProduct(UpdateProductsRequest request, List<UploadedImage> uploadedImageList) {
        // 업로드 결과: "상품인덱스:이미지순서" → s3Key
        Map<String, String> uploadedKeyMap = uploadedImageList.stream()
                .collect(Collectors.toMap(
                        u -> u.getProductIndex() + ":" + u.getImageSeq(),
                        UploadedImage::getS3Key));

        List<Product> updatedProducts = new ArrayList<>();
        List<String> deletedKeys = new ArrayList<>();
        Map<Long, List<Long>> removedInventoryIdsByProduct = new HashMap<>();

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
            // 판매글 수정이 끝날 때까지(post가 "수정 끝" API를 부르거나 복구할 때까지) 구매를 막는다
            product.startUpdating();
            updatedProducts.add(product);

            // 이미지 변경이 있을 때만
            if (productReq.getProductImageList() != null) {
                updateImages(product, productReq.getProductImageList(), uploadedKeyMap, i, deletedKeys);
            }

            // 재고(와 재고에서 분리된 선택된 옵션) 변경이 있을 때만
            if (productReq.getInventoryList() != null) {
                removedInventoryIdsByProduct.put(product.getId(),
                        updateInventories(product, productReq.getInventoryList()));
            }
        }

        productRepository.flush();

        // 응답에 빠진 재고 ID를 싣는다. 이벤트는 수정이 끝까지 성공했을 때 post가 낸다(실패해 복구되면 재고가 되살아나므로)
        List<SyncProduct> syncProductList = updatedProducts.stream()
                .map(product -> product.toSyncProduct(
                        removedInventoryIdsByProduct.getOrDefault(product.getId(), List.of())))
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

    // 재고를 요청 기준으로 맞춘다. 재고의 조합(선택된 옵션)은 바꾸지 않는다
    // - id 있음: 기존 재고의 추가금액 · 재고 수량만 바꾼다 (이미 삭제된 재고면 PRODUCT_INVENTORY_NOT_FOUND)
    // - id 없음: 새 재고 + 그 재고에서 분리된 선택된 옵션을 추가한다 (살아 있는 재고와 조합이 같으면 거부)
    // - 요청에 없는 기존 재고: 선택된 옵션과 함께 삭제 상태로 (행과 재고 ID는 남는다)
    // 빠진(삭제 상태로 바꾼) 재고 ID를 돌려준다
    private List<Long> updateInventories(Product product, List<UpdateInventory> reqInventoryList) {
        Map<Long, Inventory> activeById = product.activeInventories().stream()
                .collect(Collectors.toMap(Inventory::getId, Function.identity()));

        Set<Long> keepIds = reqInventoryList.stream()
                .map(UpdateInventory::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 먼저 지워야 같은 조합을 새로 추가하는 요청(지우고 다시 만들기)이 중복으로 걸리지 않는다
        List<Long> removedIds = activeById.values().stream()
                .filter(inventory -> !keepIds.contains(inventory.getId()))
                .map(Inventory::delete)
                .map(Inventory::getId)
                .sorted()
                .toList();

        for (UpdateInventory req : reqInventoryList) {
            if (req.getId() == null) {
                product.addInventory(
                        productOptionValueRepository.getOptionValues(CreateProductOption.optionIds(req.getOptionList())),
                        req.getAdditionalPrice(),
                        req.getStockQuantity());
            } else {
                Inventory existing = activeById.get(req.getId());
                if (existing == null) {
                    throw new ServiceException(ProductErrorCode.PRODUCT_INVENTORY_NOT_FOUND);
                }
                existing.change(req.getAdditionalPrice(), req.getStockQuantity());
            }
        }

        if (product.activeInventories().isEmpty()) {
            throw new ServiceException(ProductErrorCode.PRODUCT_INVENTORY_REQUIRED);
        }
        return removedIds;
    }
}
