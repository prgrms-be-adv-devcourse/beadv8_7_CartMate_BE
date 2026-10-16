package com.cartmate.cartmate.boundedContext.cart.domain.groupshared;

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
            throw new IllegalArgumentException("마감되거나 취소된 공동구매만 다시 만들 수 있습니다.");
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
            throw new IllegalArgumentException("이미 참여한 공동구매입니다.");
        }
        if (members.size() >= maxParticipants) {
            throw new IllegalArgumentException("참여 인원이 가득 찼습니다.");
        }
        members.add(SharedGroupPurchaseMember.create(this, memberId));
    }

    public void leave(Long memberId, LocalDateTime now) {
        validateEditable(now);
        if (isCreator(memberId)) {
            throw new IllegalArgumentException("생성자는 공동구매를 나갈 수 없습니다.");
        }
        removeMember(memberId);
    }

    public void kick(Long requesterId, Long targetMemberId, LocalDateTime now) {
        validateEditable(now);
        validateCreator(requesterId);
        if (isCreator(targetMemberId)) {
            throw new IllegalArgumentException("생성자는 내보낼 수 없습니다.");
        }
        removeMember(targetMemberId);
    }

    public void increaseMaxParticipants(Long requesterId, int maxParticipants, LocalDateTime now) {
        validateEditable(now);
        validateCreator(requesterId);
        validateMaxParticipants(maxParticipants);
        if (maxParticipants <= this.maxParticipants) {
            throw new IllegalArgumentException("최대 인원은 늘리는 것만 가능합니다.");
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
                .orElseThrow(() -> new IllegalArgumentException("공동구매에 없는 상품입니다."));
    }


    public void close(Long requesterId, Set<Long> unavailableInventoryIds, LocalDateTime now) {
        validateCreator(requesterId);
        if (!isEditable(now) && status != SharedGroupPurchaseStatus.FROZEN) {
            throw new IllegalArgumentException("마감할 수 없는 상태입니다.");
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("상품이 없는 공동구매는 마감할 수 없습니다.");
        }
        if (hasUnavailableItem(unavailableInventoryIds)) {
            throw new IllegalArgumentException("구매할 수 없는 상품이 있어 마감할 수 없습니다.");
        }
        finish(SharedGroupPurchaseStatus.CLOSED, now);
    }

    public void cancel(Long requesterId, LocalDateTime now) {
        validateCreator(requesterId);
        if (status != SharedGroupPurchaseStatus.OPEN && status != SharedGroupPurchaseStatus.FROZEN) {
            throw new IllegalArgumentException("취소할 수 없는 상태입니다.");
        }
        finish(SharedGroupPurchaseStatus.CANCELED, now);
    }

    public void closeByDeadline(Set<Long> unavailableInventoryIds, LocalDateTime now) {
        if (status != SharedGroupPurchaseStatus.OPEN || now.isBefore(editDeadlineAt)) {
            throw new IllegalStateException("자동 마감 대상이 아닙니다.");
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
            throw new IllegalStateException("마감 보류 해제 대상이 아닙니다.");
        }
        items.removeIf(item -> unavailableInventoryIds.contains(item.getInventoryId()));
        finish(items.isEmpty() ? SharedGroupPurchaseStatus.CANCELED : SharedGroupPurchaseStatus.CLOSED, now);
    }

    public boolean isEditable(LocalDateTime now) {
        return status == SharedGroupPurchaseStatus.OPEN && now.isBefore(editDeadlineAt);
    }

    private void removeMember(Long memberId) {
        SharedGroupPurchaseMember member = members.stream()
                .filter(m -> m.is(memberId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("참여하지 않은 회원입니다."));
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
            throw new IllegalArgumentException("수정이 마감된 공동구매입니다.");
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
            throw new IllegalArgumentException("공동구매 참여자가 아닙니다.");
        }
    }

    private void validateCreator(Long memberId) {
        if (!isCreator(memberId)) {
            throw new IllegalArgumentException("공동구매 생성자만 할 수 있습니다.");
        }
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank() || title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("제목은 1자 이상 " + MAX_TITLE_LENGTH + "자 이하여야 합니다.");
        }
    }

    private static void validateMaxParticipants(int maxParticipants) {
        if (maxParticipants < MIN_PARTICIPANTS || maxParticipants > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException(
                    "참여 인원은 " + MIN_PARTICIPANTS + "명 이상 " + MAX_PARTICIPANTS + "명 이하여야 합니다.");
        }
    }
}
