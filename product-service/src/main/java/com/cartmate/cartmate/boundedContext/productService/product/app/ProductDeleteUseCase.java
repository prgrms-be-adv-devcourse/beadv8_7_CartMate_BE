package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.boundedContext.productService.code.ProductErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.shared.product.event.ProductDeletedEvent;
import com.cartmate.cartmate.shared.product.event.ProductsDeletedByPostEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductDeleteUseCase {
    private final ProductRepository productRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public void deleteProductByUser(Long productId, Long sellerId) {
        Long salesPostId = productRepository.findSalesPostIdById(productId)
                .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_NOT_FOUND));

        // 같은 판매글의 상품을 잠그고 최신 상태로 읽는다
        List<Product> postProducts = productRepository.findForUpdateBySalesPostId(salesPostId);
        Product product = postProducts.stream()
                .filter(p -> p.getId().equals(productId) && p.getStatus() != ProductStatus.DELETED)
                .findFirst()
                .orElseThrow(() -> new ServiceException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getSellerId().equals(sellerId)) {
            throw new ServiceException(ProductErrorCode.PRODUCT_ACCESS_DENIED);
        }

        // 판매글에 상품이 0개가 되지 않게, 마지막 상품은 상품 삭제 대신 판매글 삭제로 안내한다
        long remaining = postProducts.stream()
                .filter(p -> p.getStatus() != ProductStatus.DELETED)
                .count();
        if (remaining <= 1) {
            throw new ServiceException(ProductErrorCode.PRODUCT_DELETE_LAST);
        }

        product.delete();

        // 상품 삭제와 같은 트랜잭션으로 outbox에 저장한다. 복제본 삭제는 post가 이벤트를 받아 처리한다
        eventPublisher.publish(new ProductDeletedEvent(product.getId(), product.getSalesPostId()));
    }

    // 판매글 삭제 요청을 받아 상품을 지우고(소프트 삭제), 같은 트랜잭션으로 삭제 완료 이벤트를 outbox에 남긴다.
    // 두 번 와도 이미 DELETED인 상품을 다시 DELETED로 바꿀 뿐이라 결과가 같다
    @Transactional
    public void deleteProductByPost(Long postId) {
        List<Long> deletedIds = productRepository.findBySalesPostId(postId).stream()
                .map(Product::delete)
                .map(Product::getId)
                .toList();

        eventPublisher.publish(new ProductsDeletedByPostEvent(postId, deletedIds));
    }

    // 판매글 등록 보상: 상품을 DB에서 지우고, 지운 상품의 사진 키를 돌려준다 (행이 사라지면 키도 알 수 없으므로 지우기 전에 모은다)
    @Transactional
    public List<String> hardDeleteByEvent(Long postId) {
        List<Product> products = productRepository.findBySalesPostId(postId);

        List<String> imageKeys = products.stream()
                .flatMap(product -> product.getProductImageList().stream())
                .map(ProductImage::getS3Key)
                .filter(Objects::nonNull)
                .toList();

        productRepository.deleteAll(products);
        return imageKeys;
    }
}
