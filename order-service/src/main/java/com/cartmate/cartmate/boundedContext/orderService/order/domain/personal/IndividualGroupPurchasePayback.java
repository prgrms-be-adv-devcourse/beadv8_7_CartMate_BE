package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "individual_group_purchase_payback")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndividualGroupPurchasePayback {

    @Id
    private Long individualGroupPurchaseId;

    @Column(nullable = false)
    private int minBuyers;

    @Column(nullable = false)
    private long paybackAmount;

    @Column(nullable = false)
    private int buyerCount;

    @Column(nullable = false)
    private boolean reached;

    @Column(nullable = false)
    private LocalDateTime judgedAt;

    private IndividualGroupPurchasePayback(Long individualGroupPurchaseId, int minBuyers, long paybackAmount,
                                           int buyerCount, LocalDateTime judgedAt) {
        this.individualGroupPurchaseId = individualGroupPurchaseId;
        this.minBuyers = minBuyers;
        this.paybackAmount = paybackAmount;
        this.buyerCount = buyerCount;
        this.reached = buyerCount >= minBuyers;
        this.judgedAt = judgedAt;
    }

    public static IndividualGroupPurchasePayback judge(Long individualGroupPurchaseId, int minBuyers,
                                                       long paybackAmount, int buyerCount, LocalDateTime judgedAt) {
        return new IndividualGroupPurchasePayback(individualGroupPurchaseId, minBuyers, paybackAmount, buyerCount,
                judgedAt);
    }
}
