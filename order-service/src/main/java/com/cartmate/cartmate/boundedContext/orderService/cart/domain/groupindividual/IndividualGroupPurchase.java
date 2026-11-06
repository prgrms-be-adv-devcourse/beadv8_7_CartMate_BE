package com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
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
@Table(
        name = "individual_group_purchase",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_individual_group_purchase_demand_product",
                columnNames = "demand_product_key"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndividualGroupPurchase {

    public static final Duration DEFAULT_SALE_PERIOD = Duration.ofHours(48);
    public static final Duration MAX_SALE_PERIOD = Duration.ofHours(48);
    public static final Duration DEMAND_DEFAULT_SALE_PERIOD = Duration.ofHours(72);
    public static final Duration DEMAND_MAX_SALE_PERIOD = Duration.ofDays(7);
    public static final int DEFAULT_PAYBACK_MIN_BUYERS = 10;
    public static final long DEFAULT_PAYBACK_AMOUNT = 1_000L;
    private static final int MIN_PAYBACK_BUYERS = 2;
    private static final int MAX_PAYBACK_BUYERS = 1_000;
    private static final long MAX_PAYBACK_AMOUNT = 100_000L;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20) default 'MEMBER'")
    private IndividualGroupPurchaseOrigin origin;

    private Long sourceProductId;

    @Column(name = "demand_product_key")
    private Long demandProductKey;

    private Integer paybackMinBuyers;

    private Long paybackAmount;

    // 판매 조건(상품 · 마감 시각 · 상태)이 바뀔 때마다 1씩 오른다. order 사본이 늦게 온 이벤트로 새 상태를 덮지 않게 한다
    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private long revision;


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
        this.origin = IndividualGroupPurchaseOrigin.MEMBER;
        this.deadlineAt = now.plus(DEFAULT_SALE_PERIOD);
        productIds.stream()
                .distinct()
                .forEach(productId -> products.add(IndividualGroupPurchaseProduct.create(this, productId)));
    }


    public static IndividualGroupPurchase create(String title, Long creatorMemberId, String inviteCode,
                                                 List<Long> productIds, LocalDateTime now) {
        return new IndividualGroupPurchase(title, creatorMemberId, inviteCode, productIds, now);
    }

    public static IndividualGroupPurchase openFromDemand(String title, Long adminMemberId, String inviteCode,
                                                         Long productId, LocalDateTime deadlineAt,
                                                         int paybackMinBuyers, long paybackAmount, LocalDateTime now) {
        validatePayback(paybackMinBuyers, paybackAmount);
        IndividualGroupPurchase purchase =
                new IndividualGroupPurchase(title, adminMemberId, inviteCode, List.of(productId), now);
        purchase.origin = IndividualGroupPurchaseOrigin.ADMIN_DEMAND;
        purchase.sourceProductId = productId;
        purchase.demandProductKey = productId;
        purchase.paybackMinBuyers = paybackMinBuyers;
        purchase.paybackAmount = paybackAmount;
        LocalDateTime requestedDeadlineAt = deadlineAt == null ? now.plus(DEMAND_DEFAULT_SALE_PERIOD) : deadlineAt;
        purchase.validateDeadline(requestedDeadlineAt, now);
        purchase.deadlineAt = requestedDeadlineAt;
        return purchase;
    }

    public boolean isFromDemand() {
        return origin == IndividualGroupPurchaseOrigin.ADMIN_DEMAND;
    }

    public void releaseDemandProductKey(LocalDateTime now) {
        if (isOpen(now)) {
            throw new ServiceException(CartErrorCode.CART_DEMAND_ALREADY_OPEN);
        }
        this.demandProductKey = null;
    }


    public void addProduct(Long requesterId, Long productId, LocalDateTime now) {
        validateCreator(requesterId);
        validateOpen(now);
        if (hasProduct(productId)) {
            throw new ServiceException(CartErrorCode.CART_INDIVIDUAL_PRODUCT_ALREADY_ON_SALE);
        }
        products.add(IndividualGroupPurchaseProduct.create(this, productId));
        revision++;
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
        revision++;
    }

    // 판매글 판매 중지: 해당 상품을 제외한다. 남은 상품이 없으면 공동구매를 종료한다. 바뀐 게 있으면 true.
    public boolean removeProductsBySalesStopped(Set<Long> productIds, LocalDateTime now) {
                if (status != IndividualGroupPurchaseStatus.OPEN) {
                    return false;
                }
                boolean removed = products.removeIf(product -> productIds.contains(product.getProductId()));
                if (!removed) {
                    return false;
                }
                if (products.isEmpty()) {
                    markClosed(now);
                } else {
                    revision++;
                }
                return true;
    }

    public List<Long> productIds() {
        return products.stream().map(IndividualGroupPurchaseProduct::getProductId).toList();
    }

    public boolean hasProduct(Long productId) {
        return products.stream().anyMatch(product -> product.is(productId));
    }


    public void changeDeadline(Long requesterId, LocalDateTime newDeadlineAt, LocalDateTime now) {
        validateCreator(requesterId);
        validateOpen(now);
        validateDeadline(newDeadlineAt, now);
        this.deadlineAt = newDeadlineAt;
        revision++;
    }

    public void close(Long requesterId, LocalDateTime now) {
        validateCreator(requesterId);
        validateNotClosed();
        markClosed(now);
    }

    // 어드민 공동구매는 만든 어드민이 아니어도 어드민이면 마감할 수 있다 (권한 확인은 /api/admin 경로가 한다)
    public void closeByAdmin(LocalDateTime now) {
        if (!isFromDemand()) {
            throw new ServiceException(CartErrorCode.CART_INDIVIDUAL_ADMIN_DEMAND_ONLY);
        }
        validateNotClosed();
        markClosed(now);
    }

    // 마감 시각이 지난 어드민 공동구매를 시스템이 닫는다. 회원 공동구매는 지금처럼 시간 비교로만 닫힌 것으로 본다.
    // 대상이 아니면(이미 닫힘 · 마감 전 · 회원 공동구매) 아무것도 하지 않고 false
    public boolean closeByDeadline(LocalDateTime now) {
        if (!isFromDemand() || status != IndividualGroupPurchaseStatus.OPEN || now.isBefore(deadlineAt)) {
            return false;
        }
        markClosed(now);
        return true;
    }

    public boolean isClosed() {
        return status == IndividualGroupPurchaseStatus.CLOSED;
    }

    // 닫는 길은 모두 여기를 지난다. 수요 상품 키를 비워 같은 상품으로 다시 열 수 있게 한다
    private void markClosed(LocalDateTime now) {
        this.status = IndividualGroupPurchaseStatus.CLOSED;
        this.closedAt = now;
        this.demandProductKey = null;
        revision++;
    }

    private void validateNotClosed() {
        if (status != IndividualGroupPurchaseStatus.OPEN) {
            throw new ServiceException(CartErrorCode.CART_INDIVIDUAL_ALREADY_CLOSED);
        }
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


    private void validateDeadline(LocalDateTime newDeadlineAt, LocalDateTime now) {
        if (!newDeadlineAt.isAfter(now)) {
            throw new ServiceException(CartErrorCode.CART_INDIVIDUAL_DEADLINE_IN_PAST);
        }
        if (isFromDemand() && newDeadlineAt.isAfter(now.plus(DEMAND_MAX_SALE_PERIOD))) {
            throw new ServiceException(CartErrorCode.CART_DEMAND_DEADLINE_TOO_LATE);
        }
        if (!isFromDemand() && newDeadlineAt.isAfter(now.plus(MAX_SALE_PERIOD))) {
            throw new ServiceException(CartErrorCode.CART_INDIVIDUAL_DEADLINE_TOO_LATE);
        }
    }

    private static void validatePayback(int paybackMinBuyers, long paybackAmount) {
        if (paybackMinBuyers < MIN_PAYBACK_BUYERS || paybackMinBuyers > MAX_PAYBACK_BUYERS
                || paybackAmount < 1 || paybackAmount > MAX_PAYBACK_AMOUNT) {
            throw new ServiceException(CartErrorCode.CART_DEMAND_INVALID_PAYBACK);
        }
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
