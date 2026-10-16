package com.cartmate.cartmate.boundedContext.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseDetailResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseSummaryResponse;
import com.cartmate.cartmate.boundedContext.cart.out.CartProductInfo;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
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

    private final SharedGroupPurchaseSupport sharedGroupPurchaseSupport;
    private final ProductClient productClient;

    SharedGroupPurchaseDetailResponse getDetail(Long memberId, Long sharedGroupPurchaseId) {
        SharedGroupPurchase purchase = sharedGroupPurchaseSupport.findWithDetails(sharedGroupPurchaseId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공동구매입니다."));
        if (!purchase.isMember(memberId)) {
            throw new IllegalArgumentException("공동구매 참여자만 볼 수 있습니다.");
        }
        Map<Long, CartProductInfo> products = productClient
                .findAllByInventoryIds(sharedGroupPurchaseSupport.inventoryIdsOf(purchase)).stream()
                .collect(Collectors.toMap(CartProductInfo::inventoryId, Function.identity()));
        return SharedGroupPurchaseDetailResponse.of(purchase, products, memberId, LocalDateTime.now());
    }

    List<SharedGroupPurchaseSummaryResponse> getMyList(Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        return sharedGroupPurchaseSupport.findAllByParticipant(memberId).stream()
                .map(purchase -> SharedGroupPurchaseSummaryResponse.of(purchase, memberId, now))
                .toList();
    }
}
