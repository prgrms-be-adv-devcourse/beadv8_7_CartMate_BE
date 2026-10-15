package com.cartmate.cartmate.domain.cart.groupindividual.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "individual_group_purchase_product",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_individual_group_purchase_product",
                columnNames = {"individual_group_purchase_id", "product_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndividualGroupPurchaseProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "individual_group_purchase_id", nullable = false)
    private IndividualGroupPurchase individualGroupPurchase;

    @Column(nullable = false)
    private Long productId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private IndividualGroupPurchaseProduct(IndividualGroupPurchase individualGroupPurchase, Long productId) {
        this.individualGroupPurchase = individualGroupPurchase;
        this.productId = productId;
    }


    static IndividualGroupPurchaseProduct create(IndividualGroupPurchase individualGroupPurchase, Long productId) {
        return new IndividualGroupPurchaseProduct(individualGroupPurchase, productId);
    }

    public boolean is(Long productId) {
        return this.productId.equals(productId);
    }
}
