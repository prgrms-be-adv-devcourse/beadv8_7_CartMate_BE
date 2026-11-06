package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import com.cartmate.cartmate.shared.cart.PaybackFunder;
import com.cartmate.cartmate.shared.cart.dto.PaybackConditionPayload;
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
public class ReplicaPaybackCondition {

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

    private ReplicaPaybackCondition(Long productId, PaybackFunder funder, Long sellerId,
                                    int minQuantity, long amountPerUnit) {
        this.productId = productId;
        this.funder = funder;
        this.sellerId = sellerId;
        this.minQuantity = minQuantity;
        this.amountPerUnit = amountPerUnit;
    }

    public static ReplicaPaybackCondition from(PaybackConditionPayload payload) {
        return new ReplicaPaybackCondition(payload.productId(), payload.funder(), payload.sellerId(),
                payload.minQuantity(), payload.amountPerUnit());
    }

    public boolean isAdmin() {
        return funder == PaybackFunder.ADMIN;
    }
}
