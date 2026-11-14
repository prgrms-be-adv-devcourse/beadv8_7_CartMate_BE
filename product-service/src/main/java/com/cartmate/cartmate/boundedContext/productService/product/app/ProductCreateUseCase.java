package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.dto.*;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.product.out.InventoryRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionValueRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.CategoryRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductCreateUseCase {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductOptionValueRepository productOptionValueRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public List<SyncProduct> saveProduct(CreateProductRequest request, List<UploadedImage> uploadedImageList) {
        Map<Integer, List<UploadedImage>> imagesByProduct = uploadedImageList.stream()
                .collect(Collectors.groupingBy(UploadedImage::getProductIndex));

        List<CreateProduct> productList = request.getProductList();
        List<SyncProduct> resultList = new ArrayList<>();
        for (int i = 0; i < productList.size(); i++) {
            CreateProduct createProduct = productList.get(i);
            Product product = productRepository.save(
                new Product(
                    createProduct.getSellerId(),
                    request.getPostId(),
                    categoryRepository.getCategory(createProduct.getCategoryId()),
                    createProduct.getName(),
                    createProduct.getDescription(),
                    createProduct.getBasePrice(),
                    ProductStatus.ON_SALE
                )
            );
            // 1. 상품정보(위) → 2. 상품 이미지
            product.addProductImage(imagesByProduct.getOrDefault(i, List.of()));
            productImageRepository.saveAll(product.getProductImageList());

            // 3. 상품 재고 → 4. 재고에서 분리된 선택된 옵션
            List<CreateInventory> inventoryList = createProduct.getInventoryList();
            if (inventoryList == null || inventoryList.isEmpty()) {
                throw new ServiceException(ProductErrorCode.PRODUCT_INVENTORY_REQUIRED);
            }
            inventoryList.forEach(inventory -> product.addInventory(
                    productOptionValueRepository.getOptionValues(CreateProductOption.optionIds(inventory.getOptionList())),
                    inventory.getAdditionalPrice(),
                    inventory.getStockQuantity()));
            inventoryRepository.saveAll(product.getInventoryList());
            productOptionRepository.saveAll(product.getProductOptionList());

            resultList.add(product.toSyncProduct());
        }

        return resultList;
    }
}
