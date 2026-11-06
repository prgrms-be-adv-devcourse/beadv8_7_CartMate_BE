package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import com.cartmate.cartmate.shared.cart.PaybackFunder;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaybackConditionJudgment {

    @Column(nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private PaybackFunder funder;

    private Long sellerId;

    @Column(nullable = false)
    private int minQuantity;

    @Column(nullable = false)
    private long amountPerUnit;

    @Column(nullable = false)
    private int confirmedQuantity;

    @Column(nullable = false)
    private boolean reached;

    @Column(nullable = false)
    private boolean applied;

    private PaybackConditionJudgment(PaybackSettlement.ConditionResult result) {
        this.productId = result.productId();
        this.funder = result.funder();
        this.sellerId = result.sellerId();
        this.minQuantity = result.minQuantity();
        this.amountPerUnit = result.amountPerUnit();
        this.confirmedQuantity = result.confirmedQuantity();
        this.reached = result.reached();
        this.applied = result.applied();
    }

    static PaybackConditionJudgment from(PaybackSettlement.ConditionResult result) {
        return new PaybackConditionJudgment(result);
    }
}
