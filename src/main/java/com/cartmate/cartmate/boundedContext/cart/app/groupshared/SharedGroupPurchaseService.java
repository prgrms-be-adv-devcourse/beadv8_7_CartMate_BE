package com.cartmate.cartmate.boundedContext.cart.app.groupshared;

import com.cartmate.cartmate.boundedContext.cart.out.CartProductInfo;
import com.cartmate.cartmate.boundedContext.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.cart.app.InviteCodeGenerator;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateRequest;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseCreateResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseDetailResponse;
import com.cartmate.cartmate.boundedContext.cart.dto.groupshared.SharedGroupPurchaseSummaryResponse;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchase;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchaseItem;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchaseMember;
import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchaseStatus;
import com.cartmate.cartmate.boundedContext.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SharedGroupPurchaseService {

    private final SharedGroupPurchaseRepository sharedGroupPurchaseRepository;
    private final ProductClient productClient;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SharedGroupPurchaseCreateResponse create(Long memberId, SharedGroupPurchaseCreateRequest request) {
        String inviteCode = inviteCodeGenerator.generateUnique(sharedGroupPurchaseRepository::existsByInviteCode);
        SharedGroupPurchase purchase = SharedGroupPurchase.create(
                request.title(), memberId, request.maxParticipants(), inviteCode, now());
        SharedGroupPurchase saved = sharedGroupPurchaseRepository.save(purchase);
        return SharedGroupPurchaseCreateResponse.from(saved);
    }


    @Transactional
    public SharedGroupPurchaseCreateResponse duplicate(Long memberId, Long sharedGroupPurchaseId) {
        SharedGroupPurchase source = getPurchase(sharedGroupPurchaseId);
        Set<Long> unavailable = productClient.findUnavailableInventoryIds(inventoryIdsOf(source));
        String inviteCode = inviteCodeGenerator.generateUnique(sharedGroupPurchaseRepository::existsByInviteCode);
        SharedGroupPurchase copy = source.duplicate(memberId, inviteCode, unavailable, now());
        SharedGroupPurchase saved = sharedGroupPurchaseRepository.save(copy);
        return SharedGroupPurchaseCreateResponse.duplicated(saved, unavailable);
    }


    @Transactional
    public Long join(Long memberId, String inviteCode) {
        SharedGroupPurchase purchase = sharedGroupPurchaseRepository.findByInviteCodeForUpdate(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 코드입니다."));
        if (purchase.isMember(memberId)) {
            return purchase.getId();
        }
        purchase.join(memberId, now());
        return purchase.getId();
    }

    @Transactional
    public void leave(Long memberId, Long sharedGroupPurchaseId) {
        getPurchaseForUpdate(sharedGroupPurchaseId).leave(memberId, now());
    }

    @Transactional
    public void kick(Long memberId, Long sharedGroupPurchaseId, Long targetMemberId) {
        getPurchaseForUpdate(sharedGroupPurchaseId).kick(memberId, targetMemberId, now());
    }

    @Transactional
    public void increaseMaxParticipants(Long memberId, Long sharedGroupPurchaseId, int maxParticipants) {
        getPurchaseForUpdate(sharedGroupPurchaseId).increaseMaxParticipants(memberId, maxParticipants, now());
    }



    @Transactional
    public void addItem(Long memberId, Long sharedGroupPurchaseId, Long inventoryId, int quantity) {
        CartProductInfo product = getPurchasableProduct(inventoryId);
        SharedGroupPurchase purchase = getPurchaseForUpdate(sharedGroupPurchaseId);
        purchase.addItem(memberId, product.productId(), inventoryId, quantity, now());
    }

    @Transactional
    public void changeItemQuantity(Long memberId, Long sharedGroupPurchaseId, Long itemId, int quantity) {
        getPurchaseForUpdate(sharedGroupPurchaseId).changeItemQuantity(memberId, itemId, quantity, now());
    }


    @Transactional
    public void changeItemOption(Long memberId, Long sharedGroupPurchaseId, Long itemId, Long inventoryId) {
        CartProductInfo product = getPurchasableProduct(inventoryId);
        SharedGroupPurchase purchase = getPurchaseForUpdate(sharedGroupPurchaseId);
        purchase.changeItemOption(memberId, itemId, product.productId(), inventoryId, now());
    }

    @Transactional
    public void removeItem(Long memberId, Long sharedGroupPurchaseId, Long itemId) {
        getPurchaseForUpdate(sharedGroupPurchaseId).removeItem(memberId, itemId, now());
    }


    @Transactional
    public void close(Long memberId, Long sharedGroupPurchaseId) {
        SharedGroupPurchase purchase = getPurchaseForUpdate(sharedGroupPurchaseId);
        Set<Long> unavailable = productClient.findUnavailableInventoryIds(inventoryIdsOf(purchase));
        purchase.close(memberId, unavailable, now());
        publishClosedEventIfClosed(purchase);
    }

    @Transactional
    public void cancel(Long memberId, Long sharedGroupPurchaseId) {
        getPurchaseForUpdate(sharedGroupPurchaseId).cancel(memberId, now());
    }

    @Transactional
    public void closeByDeadline(Long sharedGroupPurchaseId) {
        SharedGroupPurchase purchase = getPurchaseForUpdate(sharedGroupPurchaseId);
        LocalDateTime now = now();
        if (purchase.getStatus() == SharedGroupPurchaseStatus.OPEN && !purchase.isEditable(now)) {
            Set<Long> unavailable = productClient.findUnavailableInventoryIds(inventoryIdsOf(purchase));
            purchase.closeByDeadline(unavailable, now);
            publishClosedEventIfClosed(purchase);
        }
    }


    @Transactional
    public void closeByFrozenTimeout(Long sharedGroupPurchaseId) {
        SharedGroupPurchase purchase = getPurchaseForUpdate(sharedGroupPurchaseId);
        LocalDateTime now = now();
        boolean frozen = purchase.getStatus() == SharedGroupPurchaseStatus.FROZEN;
        if (frozen && !now.isBefore(purchase.getFrozenAt().plus(SharedGroupPurchase.FROZEN_PERIOD))) {
            Set<Long> unavailable = productClient.findUnavailableInventoryIds(inventoryIdsOf(purchase));
            purchase.closeByFrozenTimeout(unavailable, now);
            publishClosedEventIfClosed(purchase);
        }
    }


    public SharedGroupPurchaseDetailResponse getDetail(Long memberId, Long sharedGroupPurchaseId) {
        SharedGroupPurchase purchase = sharedGroupPurchaseRepository.findWithDetailsById(sharedGroupPurchaseId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공동구매입니다."));
        if (!purchase.isMember(memberId)) {
            throw new IllegalArgumentException("공동구매 참여자만 볼 수 있습니다.");
        }
        Map<Long, CartProductInfo> products = productClient.findAllByInventoryIds(inventoryIdsOf(purchase)).stream()
                .collect(Collectors.toMap(CartProductInfo::inventoryId, Function.identity()));
        return SharedGroupPurchaseDetailResponse.of(purchase, products, memberId, now());
    }

    public List<SharedGroupPurchaseSummaryResponse> getMyList(Long memberId) {
        LocalDateTime now = now();
        return sharedGroupPurchaseRepository.findAllByParticipant(memberId).stream()
                .map(purchase -> SharedGroupPurchaseSummaryResponse.of(purchase, memberId, now))
                .toList();
    }


    private SharedGroupPurchase getPurchaseForUpdate(Long sharedGroupPurchaseId) {
        return sharedGroupPurchaseRepository.findByIdForUpdate(sharedGroupPurchaseId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공동구매입니다."));
    }


    private void publishClosedEventIfClosed(SharedGroupPurchase purchase) {
        if (purchase.getStatus() == SharedGroupPurchaseStatus.CLOSED) {
            eventPublisher.publishEvent(toClosedEvent(purchase));
        }
    }

    // 엔티티 → 마감 이벤트 변환. shared 계약이 cart 엔티티를 몰라도 되게, 보내는 쪽(cart)에서 값만 꺼내 담는다.
    // 트랜잭션 안이라 참여자·항목(지연 로딩)을 읽어도 안전하다.
    private SharedGroupPurchaseClosedEvent toClosedEvent(SharedGroupPurchase purchase) {
        List<Long> participantMemberIds = purchase.getMembers().stream()
                .map(SharedGroupPurchaseMember::getMemberId)
                .toList();
        List<SharedGroupPurchaseClosedEvent.Item> items = purchase.getItems().stream()
                .map(item -> new SharedGroupPurchaseClosedEvent.Item(
                        item.getProductId(), item.getInventoryId(), item.getQuantity()))
                .toList();
        return new SharedGroupPurchaseClosedEvent(
                purchase.getId(),
                purchase.getCreatorMemberId(),
                participantMemberIds,
                items,
                purchase.getClosedAt()
        );
    }

    private SharedGroupPurchase getPurchase(Long sharedGroupPurchaseId) {
        return sharedGroupPurchaseRepository.findById(sharedGroupPurchaseId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공동구매입니다."));
    }

    private CartProductInfo getPurchasableProduct(Long inventoryId) {
        CartProductInfo product = productClient.findByInventoryId(inventoryId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품 옵션입니다."));
        if (!product.purchasable()) {
            throw new IllegalArgumentException("구매할 수 없는 상품입니다.");
        }
        return product;
    }

    private List<Long> inventoryIdsOf(SharedGroupPurchase purchase) {
        return purchase.getItems().stream()
                .map(SharedGroupPurchaseItem::getInventoryId)
                .toList();
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }
}
