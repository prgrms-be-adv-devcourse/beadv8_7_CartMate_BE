package com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.exception.CartErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.PaybackFunder;
import com.cartmate.cartmate.shared.cart.dto.PaybackConditionPayload;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "individual_group_purchase_payback_condition",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_individual_group_purchase_payback_condition",
                columnNames = {"individual_group_purchase_id", "product_id", "funder"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndividualGroupPurchasePaybackCondition {

    public static final int MIN_QUANTITY = 2;
    public static final int MAX_QUANTITY = 100_000;
    public static final long MAX_AMOUNT_PER_UNIT = 100_000L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "individual_group_purchase_id", nullable = false)
    private IndividualGroupPurchase individualGroupPurchase;

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

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private IndividualGroupPurchasePaybackCondition(IndividualGroupPurchase individualGroupPurchase, Long productId,
                                                    PaybackFunder funder, Long sellerId,
                                                    int minQuantity, long amountPerUnit) {
        validate(funder, sellerId, minQuantity, amountPerUnit);
        this.individualGroupPurchase = individualGroupPurchase;
        this.productId = productId;
        this.funder = funder;
        this.sellerId = sellerId;
        this.minQuantity = minQuantity;
        this.amountPerUnit = amountPerUnit;
    }

    static IndividualGroupPurchasePaybackCondition admin(IndividualGroupPurchase purchase, Long productId,
                                                         int minQuantity, long amountPerUnit) {
        return new IndividualGroupPurchasePaybackCondition(purchase, productId, PaybackFunder.ADMIN, null,
                minQuantity, amountPerUnit);
    }

    static IndividualGroupPurchasePaybackCondition seller(IndividualGroupPurchase purchase, Long productId,
                                                          Long sellerId, int minQuantity, long amountPerUnit) {
        return new IndividualGroupPurchasePaybackCondition(purchase, productId, PaybackFunder.SELLER, sellerId,
                minQuantity, amountPerUnit);
    }

    public boolean is(Long productId, PaybackFunder funder) {
        return this.productId.equals(productId) && this.funder == funder;
    }

    public PaybackConditionPayload toPayload() {
        return new PaybackConditionPayload(productId, funder, sellerId, minQuantity, amountPerUnit);
    }

    private static void validate(PaybackFunder funder, Long sellerId, int minQuantity, long amountPerUnit) {
        if (minQuantity < MIN_QUANTITY || minQuantity > MAX_QUANTITY
                || amountPerUnit < 1 || amountPerUnit > MAX_AMOUNT_PER_UNIT) {
            throw new ServiceException(CartErrorCode.CART_DEMAND_INVALID_PAYBACK);
        }
        if ((funder == PaybackFunder.SELLER) != (sellerId != null)) {
            throw new IllegalArgumentException("판매자 조건에만 sellerId가 있어야 합니다.");
        }
    }
}
