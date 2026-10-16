package com.cartmate.cartmate.boundedContext.cart.domain.groupindividual;

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

@Getter
@Entity
@Table(name = "individual_group_purchase")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndividualGroupPurchase {

    public static final Duration DEFAULT_SALE_PERIOD = Duration.ofHours(48);
    public static final Duration MAX_SALE_PERIOD = Duration.ofHours(48);
    private static final int MAX_TITLE_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;


    @Column(nullable = false)
    private Long creatorMemberId;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private IndividualGroupPurchaseStatus status;


    @Column(nullable = false, unique = true, length = 12)
    private String inviteCode;

    @Column(nullable = false)
    private LocalDateTime deadlineAt;

    private LocalDateTime closedAt;


    @OrderBy("id")
    @OneToMany(mappedBy = "individualGroupPurchase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IndividualGroupPurchaseProduct> products = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private IndividualGroupPurchase(String title, Long creatorMemberId, String inviteCode,
                                    List<Long> productIds, LocalDateTime now) {
        validateTitle(title);
        if (productIds == null || productIds.isEmpty()) {
            throw new IllegalArgumentException("판매할 상품을 하나 이상 선택해야 합니다.");
        }
        this.title = title;
        this.creatorMemberId = creatorMemberId;
        this.inviteCode = inviteCode;
        this.status = IndividualGroupPurchaseStatus.OPEN;
        this.deadlineAt = now.plus(DEFAULT_SALE_PERIOD);
        productIds.stream()
                .distinct()
                .forEach(productId -> products.add(IndividualGroupPurchaseProduct.create(this, productId)));
    }


    public static IndividualGroupPurchase create(String title, Long creatorMemberId, String inviteCode,
                                                 List<Long> productIds, LocalDateTime now) {
        return new IndividualGroupPurchase(title, creatorMemberId, inviteCode, productIds, now);
    }


    public void addProduct(Long requesterId, Long productId, LocalDateTime now) {
        validateCreator(requesterId);
        validateOpen(now);
        if (hasProduct(productId)) {
            throw new IllegalArgumentException("이미 판매 중인 상품입니다.");
        }
        products.add(IndividualGroupPurchaseProduct.create(this, productId));
    }


    public void removeProduct(Long requesterId, Long productId, boolean hasOrders, LocalDateTime now) {
        validateCreator(requesterId);
        validateOpen(now);
        if (hasOrders) {
            throw new IllegalArgumentException("이미 구매한 사람이 있는 상품은 뺄 수 없습니다.");
        }
        if (products.size() == 1) {
            throw new IllegalArgumentException("판매 상품은 하나 이상 있어야 합니다. 판매를 멈추려면 마감해 주세요.");
        }
        IndividualGroupPurchaseProduct product = products.stream()
                .filter(p -> p.is(productId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("판매 중인 상품이 아닙니다."));
        products.remove(product);
    }

    public boolean hasProduct(Long productId) {
        return products.stream().anyMatch(product -> product.is(productId));
    }


    public void changeDeadline(Long requesterId, LocalDateTime newDeadlineAt, LocalDateTime now) {
        validateCreator(requesterId);
        validateOpen(now);
        if (!newDeadlineAt.isAfter(now)) {
            throw new IllegalArgumentException("판매 마감 시각은 지금 이후여야 합니다.");
        }
        if (newDeadlineAt.isAfter(now.plus(MAX_SALE_PERIOD))) {
            throw new IllegalArgumentException("판매 마감 시각은 지금으로부터 " + MAX_SALE_PERIOD.toHours() + "시간 이내여야 합니다.");
        }
        this.deadlineAt = newDeadlineAt;
    }

    public void close(Long requesterId, LocalDateTime now) {
        validateCreator(requesterId);
        if (status != IndividualGroupPurchaseStatus.OPEN) {
            throw new IllegalArgumentException("이미 마감된 공동구매입니다.");
        }
        this.status = IndividualGroupPurchaseStatus.CLOSED;
        this.closedAt = now;
    }

    public boolean isOpen(LocalDateTime now) {
        return status == IndividualGroupPurchaseStatus.OPEN && now.isBefore(deadlineAt);
    }


    public void validateOrderable(Long productId, LocalDateTime now) {
        validateOpen(now);
        if (!hasProduct(productId)) {
            throw new IllegalArgumentException("이 공동구매에서 판매하지 않는 상품입니다.");
        }
    }

    public boolean isCreator(Long memberId) {
        return creatorMemberId.equals(memberId);
    }


    private void validateOpen(LocalDateTime now) {
        if (!isOpen(now)) {
            throw new IllegalArgumentException("판매가 마감된 공동구매입니다.");
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
}
