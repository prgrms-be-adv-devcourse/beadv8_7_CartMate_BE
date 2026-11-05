package com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "demand_payback_order_outcome")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaybackOrderOutcome {

    @Id
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private PaybackOrderOutcomeType outcome;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    private PaybackOrderOutcome(Long orderId, PaybackOrderOutcomeType outcome, LocalDateTime occurredAt) {
        this.orderId = orderId;
        this.outcome = outcome;
        this.occurredAt = occurredAt;
    }

    public static PaybackOrderOutcome confirmed(Long orderId, LocalDateTime confirmedAt) {
        return new PaybackOrderOutcome(orderId, PaybackOrderOutcomeType.CONFIRMED, confirmedAt);
    }

    public static PaybackOrderOutcome canceled(Long orderId, LocalDateTime canceledAt) {
        return new PaybackOrderOutcome(orderId, PaybackOrderOutcomeType.CANCELED, canceledAt);
    }
}
