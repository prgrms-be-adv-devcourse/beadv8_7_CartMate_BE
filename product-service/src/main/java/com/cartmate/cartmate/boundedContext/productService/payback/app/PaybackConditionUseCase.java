package com.cartmate.cartmate.boundedContext.productService.payback.app;

import com.cartmate.cartmate.boundedContext.productService.code.PaybackErrorCode;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackCondition;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.AdminPaybackConditionRequest;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.PaybackConditionResponse;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.SellerPaybackConditionRequest;
import com.cartmate.cartmate.boundedContext.productService.payback.out.PaybackConditionRepository;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.product.PaybackFunder;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class PaybackConditionUseCase {

    private final PaybackConditionRepository conditionRepository;
    private final ProductRepository productRepository;

    PaybackConditionResponse setAdmin(Long productId, AdminPaybackConditionRequest request) {
        getActiveProduct(productId);
        PaybackCondition condition = conditionRepository.findByProductIdAndFunder(productId, PaybackFunder.ADMIN)
                .map(found -> {
                    found.changeAdmin(request.minQuantity(), request.amountPerUnit(), request.overlapPolicy());
                    return found;
                })
                .orElseGet(() -> conditionRepository.save(PaybackCondition.admin(
                        productId, request.minQuantity(), request.amountPerUnit(), request.overlapPolicy())));
        return PaybackConditionResponse.from(condition);
    }

    PaybackConditionResponse setSeller(Long sellerId, Long productId, SellerPaybackConditionRequest request) {
        Product product = getActiveProduct(productId);
        validateOwner(sellerId, product);
        PaybackCondition condition = conditionRepository.findByProductIdAndFunder(productId, PaybackFunder.SELLER)
                .map(found -> {
                    found.changeSeller(product.getSellerId(), request.minQuantity(), request.amountPerUnit());
                    return found;
                })
                .orElseGet(() -> conditionRepository.save(PaybackCondition.seller(
                        productId, product.getSellerId(), request.minQuantity(), request.amountPerUnit())));
        return PaybackConditionResponse.from(condition);
    }

    void releaseAdmin(Long productId) {
        release(productId, PaybackFunder.ADMIN);
    }

    void releaseSeller(Long sellerId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ServiceException(PaybackErrorCode.PAYBACK_PRODUCT_NOT_FOUND));
        validateOwner(sellerId, product);
        release(productId, PaybackFunder.SELLER);
    }

    List<PaybackConditionResponse> findAll(Long productId) {
        return conditionRepository.findAllByProductIdOrderByFunder(productId).stream()
                .map(PaybackConditionResponse::from)
                .toList();
    }

    private void release(Long productId, PaybackFunder funder) {
        PaybackCondition condition = conditionRepository.findByProductIdAndFunder(productId, funder)
                .orElseThrow(() -> new ServiceException(PaybackErrorCode.PAYBACK_CONDITION_NOT_FOUND));
        conditionRepository.delete(condition);
    }

    private Product getActiveProduct(Long productId) {
        return productRepository.findById(productId)
                .filter(product -> product.getStatus() != ProductStatus.DELETED)
                .orElseThrow(() -> new ServiceException(PaybackErrorCode.PAYBACK_PRODUCT_NOT_FOUND));
    }

    private static void validateOwner(Long sellerId, Product product) {
        if (sellerId == null) {
            throw new ServiceException(PaybackErrorCode.PAYBACK_SELLER_ONLY);
        }
        if (!sellerId.equals(product.getSellerId())) {
            throw new ServiceException(PaybackErrorCode.PAYBACK_NOT_PRODUCT_OWNER);
        }
    }
}
