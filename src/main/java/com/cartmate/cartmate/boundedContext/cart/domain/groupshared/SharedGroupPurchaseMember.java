package com.cartmate.cartmate.boundedContext.cart.domain.groupshared;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;


@Getter
@Entity
@Table(
        name = "shared_group_purchase_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_shared_group_purchase_member",
                columnNames = {"shared_group_purchase_id", "member_id"}
        )
)


@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedGroupPurchaseMember {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_group_purchase_id", nullable = false)
    private SharedGroupPurchase sharedGroupPurchase;

    @Column(nullable = false)
    private Long memberId;


    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;


    private SharedGroupPurchaseMember(SharedGroupPurchase sharedGroupPurchase, Long memberId) {
        this.sharedGroupPurchase = sharedGroupPurchase;
        this.memberId = memberId;
    }


    static SharedGroupPurchaseMember create(SharedGroupPurchase sharedGroupPurchase, Long memberId) {
        return new SharedGroupPurchaseMember(sharedGroupPurchase, memberId);
    }

    public boolean is(Long memberId) {
        return this.memberId.equals(memberId);
    }
}
