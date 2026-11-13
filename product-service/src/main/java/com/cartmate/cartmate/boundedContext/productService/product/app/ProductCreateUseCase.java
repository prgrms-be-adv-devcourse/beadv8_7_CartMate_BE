package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.*;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionValueRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
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
    private final ProductImageRepository productImageRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductOptionValueRepository productOptionValueRepository;

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
                    createProduct.getCategoryId(),
                    createProduct.getName(),
                    createProduct.getDescription(),
                    createProduct.getBasePrice(),
                    ProductStatus.ON_SALE
                )
            );
            product.addProductImage(imagesByProduct.getOrDefault(i, List.of()));
            productImageRepository.saveAll(product.getProductImageList());

            List<CreateProductOption> optionList = createProduct.getProductOptionList();
            if (optionList != null) {
                // 옵션 ID로 옵션 값을 조회해 넣는다. 옵션 종류는 옵션 값이 가진 종류를 따른다
                optionList.forEach(option -> product.addOption(new ProductOption(product,
                        productOptionValueRepository.getOptionValue(option.getProductOptionId()), ProductStatus.ACTIVE)));
                productOptionRepository.saveAll(product.getProductOptionList());
            }
            resultList.add(product.toSyncProduct());
        }

        return resultList;
    }
}
