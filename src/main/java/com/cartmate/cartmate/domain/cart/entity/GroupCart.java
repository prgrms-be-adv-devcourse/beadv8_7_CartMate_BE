package com.cartmate.cartmate.domain.cart.entity;

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
import java.util.Set;

@Getter
@Entity
@Table(name = "group_cart")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupCart {

    public static final Duration EDIT_PERIOD = Duration.ofHours(48);
    public static final Duration FROZEN_PERIOD = Duration.ofHours(24);
    private static final int MIN_PARTICIPANTS = 2;
    private static final int MAX_PARTICIPANTS = 100;
    private static final int MAX_TITLE_LENGTH = 50;
    private static final int MAX_PERSONAL_QUANTITY = 100;

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
    @Column(nullable = false, length = 20)
    private GroupCartStatus status;

    @Column(nullable = false, unique = true, length = 12)
    private String inviteCode;

    @Column(nullable = false)
    private LocalDateTime editDeadlineAt;

    private LocalDateTime frozenAt;

    private LocalDateTime closedAt;

    private Long sourceGroupCartId;

    @OrderBy("id")
    @OneToMany(mappedBy = "groupCart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupCartMember> members = new ArrayList<>();

    @OrderBy("id")
    @OneToMany(mappedBy = "groupCart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupCartItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private GroupCart(String title, Long creatorMemberId, int maxParticipants, String inviteCode,
                      Long sourceGroupCartId, LocalDateTime now) {
        validateTitle(title);
        validateMaxParticipants(maxParticipants);
        this.title = title;
        this.creatorMemberId = creatorMemberId;
        this.maxParticipants = maxParticipants;
        this.inviteCode = inviteCode;
        this.sourceGroupCartId = sourceGroupCartId;
        this.status = GroupCartStatus.OPEN;
        this.editDeadlineAt = now.plus(EDIT_PERIOD);
        this.members.add(GroupCartMember.create(this, creatorMemberId));
    }

    public static GroupCart create(String title, Long creatorMemberId, int maxParticipants,
                                   String inviteCode, LocalDateTime now) {
        return new GroupCart(title, creatorMemberId, maxParticipants, inviteCode, null, now);
    }

    // 복제: 항목·담은 사람·최대 인원·제목만 복사, 참여자·할당은 복사하지 않는다
    public GroupCart duplicate(Long requesterId, String inviteCode, Set<Long> unavailableInventoryIds,
                               LocalDateTime now) {
        validateCreator(requesterId);
        if (status != GroupCartStatus.CLOSED && status != GroupCartStatus.CANCELED) {
            throw new IllegalArgumentException("마감되거나 취소된 공동 장바구니만 다시 만들 수 있습니다.");
        }
        GroupCart copy = new GroupCart(title, creatorMemberId, maxParticipants, inviteCode, id, now);
        items.stream()
                .filter(item -> !unavailableInventoryIds.contains(item.getInventoryId()))
                .forEach(item -> copy.items.add(GroupCartItem.copyOf(copy, item)));
        copy.autoAssignTo(creatorMemberId);
        return copy;
    }

    // ===== 참여자 =====

    public void join(Long memberId, LocalDateTime now) {
        validateEditable(now);
        if (isMember(memberId)) {
            throw new IllegalArgumentException("이미 참여한 공동 장바구니입니다.");
        }
        if (members.size() >= maxParticipants) {
            throw new IllegalArgumentException("참여 인원이 가득 찼습니다.");
        }
        members.add(GroupCartMember.create(this, memberId));
        autoAssignTo(memberId);
    }

    public void leave(Long memberId, LocalDateTime now) {
        validateEditable(now);
        if (isCreator(memberId)) {
            throw new IllegalArgumentException("생성자는 공동 장바구니를 나갈 수 없습니다.");
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

    // ===== 항목 =====

    public void addSharedItem(Long memberId, Long productId, Long inventoryId, int quantity,
                              DeliveryTarget deliveryTarget, LocalDateTime now) {
        validateEditable(now);
        validateMember(memberId);
        items.stream()
                .filter(item -> item.canMergeWith(inventoryId, deliveryTarget, memberId))
                .findFirst()
                .ifPresentOrElse(
                        item -> item.increaseQuantity(quantity),
                        () -> items.add(GroupCartItem.createShared(this, memberId, productId, inventoryId, quantity, deliveryTarget))
                );
    }

    public void addPersonalItems(Long memberId, Long productId, Long inventoryId, int quantity,
                                 DeliveryTarget deliveryTarget, LocalDateTime now) {
        validateEditable(now);
        validateMember(memberId);
        validatePersonalQuantity(quantity);
        for (int i = 0; i < quantity; i++) {
            items.add(GroupCartItem.createPersonal(this, memberId, productId, inventoryId, deliveryTarget));
        }
        members.forEach(member -> autoAssign(member.getMemberId(), productId));
    }

    public void changeItemQuantity(Long memberId, Long itemId, int quantity, LocalDateTime now) {
        validateEditable(now);
        validateMember(memberId);
        getItem(itemId).changeQuantity(quantity);
    }

    // 같은 상품의 다른 옵션(SKU)으로만 변경. 프리징 상태에서는 생성자도 가능
    public void changeItemOption(Long memberId, Long itemId, Long productId, Long inventoryId, LocalDateTime now) {
        validateEditableOrFrozenByCreator(memberId, now);
        getItem(itemId).changeOption(productId, inventoryId);
    }

    public void changeItemDeliveryTarget(Long memberId, Long itemId, DeliveryTarget deliveryTarget, LocalDateTime now) {
        validateEditable(now);
        validateMember(memberId);
        getItem(itemId).changeDeliveryTarget(deliveryTarget);
    }

    // 공동 부담 → 개인 부담: 수량만큼 1개 = 1행으로 나눈다
    public void changeItemToPersonal(Long memberId, Long itemId, LocalDateTime now) {
        validateEditable(now);
        validateMember(memberId);
        GroupCartItem item = getItem(itemId);
        if (item.isPersonal()) {
            return;
        }
        validatePersonalQuantity(item.getQuantity());
        items.remove(item);
        for (int i = 0; i < item.getQuantity(); i++) {
            items.add(GroupCartItem.createPersonal(this, item.getAddedMemberId(), item.getProductId(),
                    item.getInventoryId(), item.getDeliveryTarget()));
        }
        members.forEach(member -> autoAssign(member.getMemberId(), item.getProductId()));
    }

    public void changeItemToShared(Long memberId, Long itemId, LocalDateTime now) {
        validateEditable(now);
        validateMember(memberId);
        getItem(itemId).changeToShared();
    }

    // assigneeMemberId가 null이면 할당 해제
    public void assignItem(Long memberId, Long itemId, Long assigneeMemberId, LocalDateTime now) {
        validateEditable(now);
        validateMember(memberId);
        GroupCartItem item = getItem(itemId);
        if (assigneeMemberId == null) {
            item.unassign();
            return;
        }
        if (!isMember(assigneeMemberId)) {
            throw new IllegalArgumentException("공동 장바구니 참여자에게만 할당할 수 있습니다.");
        }
        item.assignTo(assigneeMemberId);
    }

    // 프리징 상태에서는 생성자도 삭제 가능
    public void removeItem(Long memberId, Long itemId, LocalDateTime now) {
        validateEditableOrFrozenByCreator(memberId, now);
        items.remove(getItem(itemId));
    }

    public GroupCartItem getItem(Long itemId) {
        return items.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("공동 장바구니에 없는 상품입니다."));
    }

    // ===== 마감 =====

    // 생성자 직접 마감 (OPEN 또는 FROZEN)
    public void close(Long requesterId, Set<Long> unavailableInventoryIds, LocalDateTime now) {
        validateCreator(requesterId);
        if (!isEditable(now) && status != GroupCartStatus.FROZEN) {
            throw new IllegalArgumentException("마감할 수 없는 상태입니다.");
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("상품이 없는 공동 장바구니는 마감할 수 없습니다.");
        }
        if (hasUnavailableItem(unavailableInventoryIds)) {
            throw new IllegalArgumentException("구매할 수 없는 상품이 있어 마감할 수 없습니다.");
        }
        changeStatus(GroupCartStatus.CLOSED, now);
    }

    public void cancel(Long requesterId, LocalDateTime now) {
        validateCreator(requesterId);
        if (status != GroupCartStatus.OPEN && status != GroupCartStatus.FROZEN) {
            throw new IllegalArgumentException("취소할 수 없는 상태입니다.");
        }
        changeStatus(GroupCartStatus.CANCELED, now);
    }

    // 스케줄러용: 수정 기한 도달
    public void closeByDeadline(Set<Long> unavailableInventoryIds, LocalDateTime now) {
        if (status != GroupCartStatus.OPEN || now.isBefore(editDeadlineAt)) {
            throw new IllegalStateException("자동 마감 대상이 아닙니다.");
        }
        if (items.isEmpty()) {
            changeStatus(GroupCartStatus.CANCELED, now);
        } else if (hasUnavailableItem(unavailableInventoryIds)) {
            this.status = GroupCartStatus.FROZEN;
            this.frozenAt = now;
        } else {
            changeStatus(GroupCartStatus.CLOSED, now);
        }
    }

    // 스케줄러용: 마감 보류 유예 시간 초과 → 판매 불가 상품을 빼고 마감
    public void closeByFrozenTimeout(Set<Long> unavailableInventoryIds, LocalDateTime now) {
        if (status != GroupCartStatus.FROZEN || now.isBefore(frozenAt.plus(FROZEN_PERIOD))) {
            throw new IllegalStateException("마감 보류 해제 대상이 아닙니다.");
        }
        items.removeIf(item -> unavailableInventoryIds.contains(item.getInventoryId()));
        changeStatus(items.isEmpty() ? GroupCartStatus.CANCELED : GroupCartStatus.CLOSED, now);
    }

    public boolean isEditable(LocalDateTime now) {
        return status == GroupCartStatus.OPEN && now.isBefore(editDeadlineAt);
    }

    // ===== 내부 =====

    // 상품마다, 아직 그 상품을 할당받지 않았다면 미할당 개인 부담 항목 하나를 할당
    private void autoAssignTo(Long memberId) {
        items.stream()
                .filter(GroupCartItem::isPersonal)
                .map(GroupCartItem::getProductId)
                .distinct()
                .toList()
                .forEach(productId -> autoAssign(memberId, productId));
    }

    private void autoAssign(Long memberId, Long productId) {
        boolean alreadyAssigned = items.stream()
                .anyMatch(item -> item.isPersonal() && item.getProductId().equals(productId) && item.isAssignedTo(memberId));
        if (alreadyAssigned) {
            return;
        }
        items.stream()
                .filter(item -> item.isPersonal() && item.getProductId().equals(productId) && !item.isAssigned())
                .findFirst()
                .ifPresent(item -> item.assignTo(memberId));
    }

    private void removeMember(Long memberId) {
        GroupCartMember member = members.stream()
                .filter(m -> m.is(memberId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("참여하지 않은 회원입니다."));
        members.remove(member);
        items.stream()
                .filter(item -> item.isAssignedTo(memberId))
                .forEach(GroupCartItem::unassign);
    }

    private boolean hasUnavailableItem(Set<Long> unavailableInventoryIds) {
        return items.stream().anyMatch(item -> unavailableInventoryIds.contains(item.getInventoryId()));
    }

    private void changeStatus(GroupCartStatus status, LocalDateTime now) {
        this.status = status;
        this.closedAt = now;
    }

    private void validateEditable(LocalDateTime now) {
        if (!isEditable(now)) {
            throw new IllegalArgumentException("수정이 마감된 공동 장바구니입니다.");
        }
    }

    private void validateEditableOrFrozenByCreator(Long memberId, LocalDateTime now) {
        if (status == GroupCartStatus.FROZEN && isCreator(memberId)) {
            return;
        }
        validateEditable(now);
        validateMember(memberId);
    }

    private void validateMember(Long memberId) {
        if (!isMember(memberId)) {
            throw new IllegalArgumentException("공동 장바구니 참여자가 아닙니다.");
        }
    }

    private void validateCreator(Long memberId) {
        if (!isCreator(memberId)) {
            throw new IllegalArgumentException("공동 장바구니 생성자만 할 수 있습니다.");
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

    private static void validatePersonalQuantity(int quantity) {
        if (quantity < 1 || quantity > MAX_PERSONAL_QUANTITY) {
            throw new IllegalArgumentException("개인 부담 상품은 한 번에 1개 이상 " + MAX_PERSONAL_QUANTITY + "개 이하로 담을 수 있습니다.");
        }
    }
}
