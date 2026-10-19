package com.cartmate.cartmate.boundedContext.cart.app.groupshared;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseDetailResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseSummaryResponse;
import com.cartmate.cartmate.boundedContext.cart.out.CartProductInfo;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupshared.SharedGroupPurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class SharedGroupPurchaseQueryUseCase {

    private final SharedGroupPurchaseRepository sharedGroupPurchaseRepository;
    private final SharedGroupPurchaseSupport sharedGroupPurchaseSupport;
    private final ProductClient productClient;

    SharedGroupPurchaseDetailResponse getDetail(Long memberId, Long sharedGroupPurchaseId) {
        SharedGroupPurchase purchase = sharedGroupPurchaseRepository.findWithDetailsById(sharedGroupPurchaseId)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_GROUP_PURCHASE_NOT_FOUND));
        if (!purchase.isMember(memberId)) {
            throw new ServiceException(CartErrorCode.CART_SHARED_PARTICIPANT_ONLY_VIEW);
        }
        Map<Long, CartProductInfo> products = productClient
                .findAllByInventoryIds(sharedGroupPurchaseSupport.inventoryIdsOf(purchase)).stream()
                .collect(Collectors.toMap(CartProductInfo::inventoryId, Function.identity()));
        return SharedGroupPurchaseDetailResponse.of(purchase, products, memberId, LocalDateTime.now());
    }

    List<SharedGroupPurchaseSummaryResponse> getMyList(Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        return sharedGroupPurchaseRepository.findAllByParticipant(memberId).stream()
                .map(purchase -> SharedGroupPurchaseSummaryResponse.of(purchase, memberId, now))
                .toList();
    }
}
