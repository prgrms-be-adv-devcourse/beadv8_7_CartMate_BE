package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.product.dto.UploadedImage;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import com.cartmate.cartmate.shared.product.dto.CreateProduct;
import com.cartmate.cartmate.shared.product.dto.CreateProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductCreateUseCase {
    private final ProductRepository productRepository;

    @Transactional
    public List<CreateProduct> saveProduct(CreateProductRequest request, List<UploadedImage> uploadedImageList) {
        Map<Integer, List<UploadedImage>> imagesByProduct = uploadedImageList.stream()
                .collect(Collectors.groupingBy(UploadedImage::getProductIndex));

        List<CreateProduct> productList = request.getProductList();
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
        }

        return productList;
    }
}
