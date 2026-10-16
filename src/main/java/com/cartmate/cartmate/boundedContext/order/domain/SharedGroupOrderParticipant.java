package com.cartmate.cartmate.boundedContext.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Getter
@Entity
@Table(
        name = "shared_group_order_participant",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_shared_group_order_participant",
                columnNames = {"shared_group_order_id", "member_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedGroupOrderParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_group_order_id", nullable = false)
    private SharedGroupOrder sharedGroupOrder;

    @Column(nullable = false)
    private Long memberId;


    @Column(nullable = false)
    private long shareAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private SharedGroupOrderParticipantStatus status;

    private LocalDateTime paidAt;

    @Column(nullable = false)
    private long refundedAmount;


    @Version
    private Long version;

    private SharedGroupOrderParticipant(SharedGroupOrder sharedGroupOrder, Long memberId, long shareAmount) {
        if (shareAmount < 0) {
            throw new IllegalArgumentException("부담 금액은 0원 이상이어야 합니다.");
        }
        this.sharedGroupOrder = sharedGroupOrder;
        this.memberId = memberId;
        this.shareAmount = shareAmount;
        this.status = SharedGroupOrderParticipantStatus.UNPAID;
        this.refundedAmount = 0L;
    }

    static SharedGroupOrderParticipant create(SharedGroupOrder sharedGroupOrder, Long memberId, long shareAmount) {
        return new SharedGroupOrderParticipant(sharedGroupOrder, memberId, shareAmount);
    }

    public boolean is(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public boolean isPaid() {
        return status == SharedGroupOrderParticipantStatus.PAID;
    }

    void markPaid(LocalDateTime paidAt) {
        if (status != SharedGroupOrderParticipantStatus.UNPAID) {
            throw new IllegalArgumentException("이미 결제했거나 환불된 참여자입니다.");
        }
        this.status = SharedGroupOrderParticipantStatus.PAID;
        this.paidAt = paidAt;
    }

    long refundAll() {
        if (status != SharedGroupOrderParticipantStatus.PAID) {
            return 0L;
        }
        long amount = shareAmount - refundedAmount;
        this.refundedAmount = shareAmount;
        this.status = SharedGroupOrderParticipantStatus.REFUNDED;
        return amount;
    }

    void addRefund(long amount) {
        if (refundedAmount + amount > shareAmount) {
            throw new IllegalStateException("낸 금액보다 많이 환불할 수 없습니다.");
        }
        this.refundedAmount += amount;
    }
}
