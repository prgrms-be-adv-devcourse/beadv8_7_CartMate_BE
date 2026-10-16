package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.product.dto.upload.UploadedImage;
import com.cartmate.cartmate.boundedContext.product.out.ProductImageRepository;
import com.cartmate.cartmate.shared.product.dto.create.CreateProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductImageSaveUseCase {
    private final ProductImageRepository productImageRepository;

    @Transactional
    public void saveProductImage(List<CreateProduct> productList,
                                 List<UploadedImage> uploadedImageList) {

        Map<Integer, List<UploadedImage>> imagesByProduct = uploadedImageList.stream()
                .collect(Collectors.groupingBy(UploadedImage::getProductIndex));

        List<ProductImage> productImages = new ArrayList<>();

        for (int i = 0; i < productList.size(); i++) {
            CreateProduct product = productList.get(i);
            List<UploadedImage> images = imagesByProduct.getOrDefault(i, List.of());

            for (UploadedImage image : images) {
                productImages.add(new ProductImage(
                        product.getSellerId(),
                        product.getId(),
                        image.getS3Key(),
                        image.getImageSeq()
                ));
            }
        }

        productImageRepository.saveAll(productImages);
    }
}
