package com.cartmate.cartmate.domain.cart.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "group_cart_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_cart_member_cart_member",
                columnNames = {"group_cart_id", "member_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupCartMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_cart_id", nullable = false)
    private GroupCart groupCart;

    @Column(nullable = false)
    private Long memberId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private GroupCartMember(GroupCart groupCart, Long memberId) {
        this.groupCart = groupCart;
        this.memberId = memberId;
    }

    static GroupCartMember create(GroupCart groupCart, Long memberId) {
        return new GroupCartMember(groupCart, memberId);
    }

    public boolean is(Long memberId) {
        return this.memberId.equals(memberId);
    }
}
