package com.cartmate.cartmate.boundedContext.productService.payback.domain;

import com.cartmate.cartmate.shared.product.PaybackFunder;
import com.cartmate.cartmate.shared.product.PaybackOverlapPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode(of = {"productId", "funder"})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaybackConditionSnapshot {

    @Column(nullable = false)
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

    private PaybackConditionSnapshot(Long productId, PaybackFunder funder, Long sellerId, int minQuantity,
                                     long amountPerUnit, PaybackOverlapPolicy overlapPolicy) {
        this.productId = productId;
        this.funder = funder;
        this.sellerId = sellerId;
        this.minQuantity = minQuantity;
        this.amountPerUnit = amountPerUnit;
        this.overlapPolicy = overlapPolicy;
    }

    public static PaybackConditionSnapshot of(Long productId, PaybackFunder funder, Long sellerId, int minQuantity,
                                              long amountPerUnit, PaybackOverlapPolicy overlapPolicy) {
        return new PaybackConditionSnapshot(productId, funder, sellerId, minQuantity, amountPerUnit, overlapPolicy);
    }

    public boolean isAdmin() {
        return funder == PaybackFunder.ADMIN;
    }
}
