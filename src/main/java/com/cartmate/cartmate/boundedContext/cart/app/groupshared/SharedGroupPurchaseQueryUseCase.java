package com.cartmate.cartmate.boundedContext.cart.app.groupshared;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchaseItem;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchaseMember;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseDetailResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseSummaryResponse;
import com.cartmate.cartmate.boundedContext.cart.out.CartProductInfo;
import com.cartmate.cartmate.boundedContext.cart.out.MemberClient;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.cart.out.groupshared.SharedGroupPurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class SharedGroupPurchaseQueryUseCase {

    private final SharedGroupPurchaseRepository sharedGroupPurchaseRepository;
    private final SharedGroupPurchaseSupport sharedGroupPurchaseSupport;
    private final ProductClient productClient;
    private final MemberClient memberClient;

    Map<Long, String> findTitles(Collection<Long> sharedGroupPurchaseIds) {
        return sharedGroupPurchaseRepository.findTitlesByIds(sharedGroupPurchaseIds);
    }

    SharedGroupPurchaseDetailResponse getDetail(Long memberId, Long sharedGroupPurchaseId) {
        SharedGroupPurchase purchase = sharedGroupPurchaseRepository.findWithDetailsById(sharedGroupPurchaseId)
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_GROUP_PURCHASE_NOT_FOUND));
        if (!purchase.isMember(memberId)) {
            throw new ServiceException(CartErrorCode.CART_SHARED_PARTICIPANT_ONLY_VIEW);
        }
        Map<Long, CartProductInfo> products = productClient
                .findAllByInventoryIds(sharedGroupPurchaseSupport.inventoryIdsOf(purchase)).stream()
                .collect(Collectors.toMap(CartProductInfo::inventoryId, Function.identity()));
        return SharedGroupPurchaseDetailResponse.of(purchase, products, nicknamesOf(purchase), memberId, LocalDateTime.now());
    }

    List<SharedGroupPurchaseSummaryResponse> getMyList(Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        return sharedGroupPurchaseRepository.findAllByParticipant(memberId).stream()
                .map(purchase -> SharedGroupPurchaseSummaryResponse.of(purchase, memberId, now))
                .toList();
    }

    private Map<Long, String> nicknamesOf(SharedGroupPurchase purchase) {
        Set<Long> memberIds = new HashSet<>();
        memberIds.add(purchase.getCreatorMemberId());
        purchase.getMembers().stream().map(SharedGroupPurchaseMember::getMemberId).forEach(memberIds::add);
        purchase.getItems().stream().map(SharedGroupPurchaseItem::getUpdatedMemberId).forEach(memberIds::add);
        return memberClient.findNicknames(memberIds);
    }
}
