package com.cartmate.cartmate.boundedContext.cart.domain.groupshared;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Getter
@Entity
@Table(name = "shared_group_purchase")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedGroupPurchase {


    public static final Duration EDIT_PERIOD = Duration.ofHours(48);
    public static final Duration FROZEN_PERIOD = Duration.ofHours(24);
    private static final int MIN_PARTICIPANTS = 2;
    private static final int MAX_PARTICIPANTS = 100;
    private static final int MAX_TITLE_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;


    @Column(nullable = false)
    private Long creatorMemberId;


    @Column(nullable = false)
    private int maxParticipants;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private SharedGroupPurchaseStatus status;


    @Column(nullable = false, unique = true, length = 12)
    private String inviteCode;


    @Column(nullable = false)
    private LocalDateTime editDeadlineAt;

    private LocalDateTime frozenAt;

    private LocalDateTime closedAt;

    private Long sourceSharedGroupPurchaseId;


    @OrderBy("id")
    @OneToMany(mappedBy = "sharedGroupPurchase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SharedGroupPurchaseMember> members = new ArrayList<>();

    @OrderBy("id")
    @OneToMany(mappedBy = "sharedGroupPurchase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SharedGroupPurchaseItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private SharedGroupPurchase(String title, Long creatorMemberId, int maxParticipants, String inviteCode,
                                Long sourceSharedGroupPurchaseId, LocalDateTime now) {
        validateTitle(title);
        validateMaxParticipants(maxParticipants);
        this.title = title;
        this.creatorMemberId = creatorMemberId;
        this.maxParticipants = maxParticipants;
        this.inviteCode = inviteCode;
        this.sourceSharedGroupPurchaseId = sourceSharedGroupPurchaseId;
        this.status = SharedGroupPurchaseStatus.OPEN;
        this.editDeadlineAt = now.plus(EDIT_PERIOD);
        this.members.add(SharedGroupPurchaseMember.create(this, creatorMemberId));
    }

    public static SharedGroupPurchase create(String title, Long creatorMemberId, int maxParticipants,
                                             String inviteCode, LocalDateTime now) {
        return new SharedGroupPurchase(title, creatorMemberId, maxParticipants, inviteCode, null, now);
    }


    public SharedGroupPurchase duplicate(Long requesterId, String inviteCode, Set<Long> unavailableInventoryIds,
                                         LocalDateTime now) {
        validateCreator(requesterId);
        if (status != SharedGroupPurchaseStatus.CLOSED && status != SharedGroupPurchaseStatus.CANCELED) {
            throw new ServiceException(CartErrorCode.CART_SHARED_NOT_DUPLICABLE);
        }
        SharedGroupPurchase copy = new SharedGroupPurchase(title, creatorMemberId, maxParticipants, inviteCode, id, now);
        items.stream()
                .filter(item -> !unavailableInventoryIds.contains(item.getInventoryId()))
                .forEach(item -> copy.items.add(SharedGroupPurchaseItem.copyOf(copy, item, requesterId)));
        return copy;
    }


    public void join(Long memberId, LocalDateTime now) {
        validateEditable(now);
        if (isMember(memberId)) {
            throw new ServiceException(CartErrorCode.CART_SHARED_ALREADY_JOINED);
        }
        if (members.size() >= maxParticipants) {
            throw new ServiceException(CartErrorCode.CART_SHARED_FULL);
        }
        members.add(SharedGroupPurchaseMember.create(this, memberId));
    }

    public void leave(Long memberId, LocalDateTime now) {
        validateEditable(now);
        if (isCreator(memberId)) {
            throw new ServiceException(CartErrorCode.CART_SHARED_CREATOR_CANNOT_LEAVE);
        }
        removeMember(memberId);
    }

    public void kick(Long requesterId, Long targetMemberId, LocalDateTime now) {
        validateEditable(now);
        validateCreator(requesterId);
        if (isCreator(targetMemberId)) {
            throw new ServiceException(CartErrorCode.CART_SHARED_CREATOR_CANNOT_BE_KICKED);
        }
        removeMember(targetMemberId);
    }

    public void increaseMaxParticipants(Long requesterId, int maxParticipants, LocalDateTime now) {
        validateEditable(now);
        validateCreator(requesterId);
        validateMaxParticipants(maxParticipants);
        if (maxParticipants <= this.maxParticipants) {
            throw new ServiceException(CartErrorCode.CART_SHARED_MAX_PARTICIPANTS_DECREASE);
        }
        this.maxParticipants = maxParticipants;
    }

    public boolean isMember(Long memberId) {
        return members.stream().anyMatch(member -> member.is(memberId));
    }

    public boolean isCreator(Long memberId) {
        return creatorMemberId.equals(memberId);
    }


    public void addItem(Long memberId, Long productId, Long inventoryId, int quantity, LocalDateTime now) {
        validateEditable(now);
        validateMember(memberId);
        findItemByInventoryId(inventoryId)
                .ifPresentOrElse(
                        item -> item.increaseQuantity(quantity, memberId),
                        () -> items.add(SharedGroupPurchaseItem.create(this, memberId, productId, inventoryId, quantity))
                );
    }

    public void changeItemQuantity(Long memberId, Long itemId, int quantity, LocalDateTime now) {
        validateEditable(now);
        validateMember(memberId);
        getItem(itemId).changeQuantity(quantity, memberId);
    }

    public SharedGroupPurchaseItem adjustItemQuantity(Long memberId, Long itemId, int delta, LocalDateTime now) {
        validateEditable(now);
        validateMember(memberId);
        SharedGroupPurchaseItem item = getItem(itemId);
        item.adjustQuantity(delta, memberId);
        return item;
    }


    public void changeItemOption(Long memberId, Long itemId, Long productId, Long inventoryId, LocalDateTime now) {
        validateEditableOrFrozenByCreator(memberId, now);
        SharedGroupPurchaseItem item = getItem(itemId);
        item.validateSameProduct(productId);
        if (item.isSameInventory(inventoryId)) {
            return;
        }
        findItemByInventoryId(inventoryId)
                .ifPresentOrElse(
                        existing -> {
                            existing.increaseQuantity(item.getQuantity(), memberId);
                            items.remove(item);
                        },
                        () -> item.changeOption(productId, inventoryId, memberId)
                );
    }

    public void removeItem(Long memberId, Long itemId, LocalDateTime now) {
        validateEditableOrFrozenByCreator(memberId, now);
        items.remove(getItem(itemId));
    }

    public SharedGroupPurchaseItem getItem(Long itemId) {
        return items.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_SHARED_ITEM_NOT_FOUND));
    }


    public void close(Long requesterId, Set<Long> unavailableInventoryIds, LocalDateTime now) {
        validateCreator(requesterId);
        if (!isEditable(now) && status != SharedGroupPurchaseStatus.FROZEN) {
            throw new ServiceException(CartErrorCode.CART_SHARED_NOT_CLOSABLE);
        }
        if (items.isEmpty()) {
            throw new ServiceException(CartErrorCode.CART_SHARED_EMPTY_ITEMS);
        }
        if (hasUnavailableItem(unavailableInventoryIds)) {
            throw new ServiceException(CartErrorCode.CART_SHARED_HAS_UNAVAILABLE_ITEMS);
        }
        finish(SharedGroupPurchaseStatus.CLOSED, now);
    }

    public void cancel(Long requesterId, LocalDateTime now) {
        validateCreator(requesterId);
        if (status != SharedGroupPurchaseStatus.OPEN && status != SharedGroupPurchaseStatus.FROZEN) {
            throw new ServiceException(CartErrorCode.CART_SHARED_NOT_CANCELABLE);
        }
        finish(SharedGroupPurchaseStatus.CANCELED, now);
    }

    public void closeByDeadline(Set<Long> unavailableInventoryIds, LocalDateTime now) {
        if (status != SharedGroupPurchaseStatus.OPEN || now.isBefore(editDeadlineAt)) {
            throw new ServiceException(CartErrorCode.CART_SHARED_NOT_AUTO_CLOSE_TARGET);
        }
        if (items.isEmpty()) {
            finish(SharedGroupPurchaseStatus.CANCELED, now);
        } else if (hasUnavailableItem(unavailableInventoryIds)) {
            this.status = SharedGroupPurchaseStatus.FROZEN;
            this.frozenAt = now;
        } else {
            finish(SharedGroupPurchaseStatus.CLOSED, now);
        }
    }

    public void closeByFrozenTimeout(Set<Long> unavailableInventoryIds, LocalDateTime now) {
        if (status != SharedGroupPurchaseStatus.FROZEN || now.isBefore(frozenAt.plus(FROZEN_PERIOD))) {
            throw new ServiceException(CartErrorCode.CART_SHARED_NOT_FROZEN_RELEASE_TARGET);
        }
        items.removeIf(item -> unavailableInventoryIds.contains(item.getInventoryId()));
        finish(items.isEmpty() ? SharedGroupPurchaseStatus.CANCELED : SharedGroupPurchaseStatus.CLOSED, now);
    }

    // 판매글 판매 중지: 담긴 상품을 빼고 계속 모집한다. 남은 상품이 없으면 공동구매를 취소한다. 바뀐 게 있으면 true.
    public boolean removeItemsBySalesStopped(Set<Long> productIds, LocalDateTime now) {
        if (status != SharedGroupPurchaseStatus.OPEN && status != SharedGroupPurchaseStatus.FROZEN) {
            return false;
        }
        boolean removed = items.removeIf(item -> productIds.contains(item.getProductId()));
        if (removed && items.isEmpty()) {
            finish(SharedGroupPurchaseStatus.CANCELED, now);
        }
        return removed;
    }

    public boolean isEditable(LocalDateTime now) {
        return status == SharedGroupPurchaseStatus.OPEN && now.isBefore(editDeadlineAt);
    }

    private void removeMember(Long memberId) {
        SharedGroupPurchaseMember member = members.stream()
                .filter(m -> m.is(memberId))
                .findFirst()
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_SHARED_MEMBER_NOT_FOUND));
        members.remove(member);
    }

    private Optional<SharedGroupPurchaseItem> findItemByInventoryId(Long inventoryId) {
        return items.stream()
                .filter(item -> item.isSameInventory(inventoryId))
                .findFirst();
    }

    private boolean hasUnavailableItem(Set<Long> unavailableInventoryIds) {
        return items.stream().anyMatch(item -> unavailableInventoryIds.contains(item.getInventoryId()));
    }

    private void finish(SharedGroupPurchaseStatus status, LocalDateTime now) {
        this.status = status;
        this.closedAt = now;
    }

    private void validateEditable(LocalDateTime now) {
        if (!isEditable(now)) {
            throw new ServiceException(CartErrorCode.CART_SHARED_EDIT_CLOSED);
        }
    }

    private void validateEditableOrFrozenByCreator(Long memberId, LocalDateTime now) {
        if (status == SharedGroupPurchaseStatus.FROZEN && isCreator(memberId)) {
            return;
        }
        validateEditable(now);
        validateMember(memberId);
    }

    private void validateMember(Long memberId) {
        if (!isMember(memberId)) {
            throw new ServiceException(CartErrorCode.CART_SHARED_NOT_PARTICIPANT);
        }
    }

    private void validateCreator(Long memberId) {
        if (!isCreator(memberId)) {
            throw new ServiceException(CartErrorCode.CART_CREATOR_ONLY);
        }
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank() || title.length() > MAX_TITLE_LENGTH) {
            throw new ServiceException(CartErrorCode.CART_INVALID_TITLE);
        }
    }

    private static void validateMaxParticipants(int maxParticipants) {
        if (maxParticipants < MIN_PARTICIPANTS || maxParticipants > MAX_PARTICIPANTS) {
            throw new ServiceException(CartErrorCode.CART_SHARED_INVALID_MAX_PARTICIPANTS);
        }
    }
}
