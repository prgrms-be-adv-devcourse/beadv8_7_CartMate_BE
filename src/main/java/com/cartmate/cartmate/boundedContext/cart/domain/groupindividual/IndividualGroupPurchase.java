package com.cartmate.cartmate.boundedContext.cart.domain.groupindividual;

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
import java.util.Set;

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
            throw new ServiceException(CartErrorCode.CART_INDIVIDUAL_PRODUCTS_REQUIRED);
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
            throw new ServiceException(CartErrorCode.CART_INDIVIDUAL_PRODUCT_ALREADY_ON_SALE);
        }
        products.add(IndividualGroupPurchaseProduct.create(this, productId));
    }


    public void removeProduct(Long requesterId, Long productId, boolean hasOrders, LocalDateTime now) {
        validateCreator(requesterId);
        validateOpen(now);
        if (hasOrders) {
            throw new ServiceException(CartErrorCode.CART_INDIVIDUAL_PRODUCT_HAS_ORDERS);
        }
        if (products.size() == 1) {
            throw new ServiceException(CartErrorCode.CART_INDIVIDUAL_LAST_PRODUCT);
        }
        IndividualGroupPurchaseProduct product = products.stream()
                .filter(p -> p.is(productId))
                .findFirst()
                .orElseThrow(() -> new ServiceException(CartErrorCode.CART_INDIVIDUAL_PRODUCT_NOT_ON_SALE));
        products.remove(product);
    }

    // 판매글 판매 중지: 해당 상품을 제외한다. 남은 상품이 없으면 공동구매를 종료한다. 바뀐 게 있으면 true.
    public boolean removeProductsBySalesStopped(Set<Long> productIds, LocalDateTime now) {
                if (status != IndividualGroupPurchaseStatus.OPEN) {
                    return false;
                }
                boolean removed = products.removeIf(product -> productIds.contains(product.getProductId()));
                if (removed && products.isEmpty()) {
                    this.status = IndividualGroupPurchaseStatus.CLOSED;
                    this.closedAt = now;
                }
                return removed;
    }

    public boolean hasProduct(Long productId) {
        return products.stream().anyMatch(product -> product.is(productId));
    }


    public void changeDeadline(Long requesterId, LocalDateTime newDeadlineAt, LocalDateTime now) {
        validateCreator(requesterId);
        validateOpen(now);
        if (!newDeadlineAt.isAfter(now)) {
            throw new ServiceException(CartErrorCode.CART_INDIVIDUAL_DEADLINE_IN_PAST);
        }
        if (newDeadlineAt.isAfter(now.plus(MAX_SALE_PERIOD))) {
            throw new ServiceException(CartErrorCode.CART_INDIVIDUAL_DEADLINE_TOO_LATE);
        }
        this.deadlineAt = newDeadlineAt;
    }

    public void close(Long requesterId, LocalDateTime now) {
        validateCreator(requesterId);
        if (status != IndividualGroupPurchaseStatus.OPEN) {
            throw new ServiceException(CartErrorCode.CART_INDIVIDUAL_ALREADY_CLOSED);
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
            throw new ServiceException(CartErrorCode.CART_INDIVIDUAL_PRODUCT_NOT_SOLD);
        }
    }

    public boolean isCreator(Long memberId) {
        return creatorMemberId.equals(memberId);
    }


    private void validateOpen(LocalDateTime now) {
        if (!isOpen(now)) {
            throw new ServiceException(CartErrorCode.CART_INDIVIDUAL_SALE_CLOSED);
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
}
