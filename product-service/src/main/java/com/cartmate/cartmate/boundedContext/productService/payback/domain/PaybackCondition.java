package com.cartmate.cartmate.boundedContext.productService.payback.domain;

import com.cartmate.cartmate.boundedContext.productService.code.PaybackErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.product.PaybackFunder;
import com.cartmate.cartmate.shared.product.PaybackOverlapPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "product_payback_condition",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_payback_condition_product_funder",
                columnNames = {"product_id", "funder"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaybackCondition {

    public static final int MIN_QUANTITY_LOWER = 2;
    public static final int MIN_QUANTITY_UPPER = 100_000;
    public static final long AMOUNT_PER_UNIT_LOWER = 1L;
    public static final long AMOUNT_PER_UNIT_UPPER = 100_000L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaybackFunder funder;

    private Long sellerId;

    @Column(nullable = false)
    private int minQuantity;

    @Column(nullable = false)
    private long amountPerUnit;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaybackOverlapPolicy overlapPolicy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private PaybackCondition(Long productId, PaybackFunder funder, Long sellerId, int minQuantity,
                             long amountPerUnit, PaybackOverlapPolicy overlapPolicy) {
        validate(minQuantity, amountPerUnit);
        this.productId = productId;
        this.funder = funder;
        this.sellerId = sellerId;
        this.minQuantity = minQuantity;
        this.amountPerUnit = amountPerUnit;
        this.overlapPolicy = overlapPolicy;
    }

    public static PaybackCondition admin(Long productId, int minQuantity, long amountPerUnit,
                                         PaybackOverlapPolicy overlapPolicy) {
        return new PaybackCondition(productId, PaybackFunder.ADMIN, null, minQuantity, amountPerUnit,
                overlapPolicyOrDefault(overlapPolicy));
    }

    public static PaybackCondition seller(Long productId, Long sellerId, int minQuantity, long amountPerUnit) {
        return new PaybackCondition(productId, PaybackFunder.SELLER, sellerId, minQuantity, amountPerUnit, null);
    }

    public void changeAdmin(int minQuantity, long amountPerUnit, PaybackOverlapPolicy overlapPolicy) {
        validate(minQuantity, amountPerUnit);
        this.minQuantity = minQuantity;
        this.amountPerUnit = amountPerUnit;
        this.overlapPolicy = overlapPolicyOrDefault(overlapPolicy);
    }

    public void changeSeller(Long sellerId, int minQuantity, long amountPerUnit) {
        validate(minQuantity, amountPerUnit);
        this.sellerId = sellerId;
        this.minQuantity = minQuantity;
        this.amountPerUnit = amountPerUnit;
    }

    public PaybackConditionSnapshot toSnapshot() {
        return PaybackConditionSnapshot.of(productId, funder, sellerId, minQuantity, amountPerUnit, overlapPolicy);
    }

    private static PaybackOverlapPolicy overlapPolicyOrDefault(PaybackOverlapPolicy overlapPolicy) {
        return overlapPolicy == null ? PaybackOverlapPolicy.BOTH : overlapPolicy;
    }

    private static void validate(int minQuantity, long amountPerUnit) {
        if (minQuantity < MIN_QUANTITY_LOWER || minQuantity > MIN_QUANTITY_UPPER
                || amountPerUnit < AMOUNT_PER_UNIT_LOWER || amountPerUnit > AMOUNT_PER_UNIT_UPPER) {
            throw new ServiceException(PaybackErrorCode.PAYBACK_INVALID_CONDITION);
        }
    }
}
