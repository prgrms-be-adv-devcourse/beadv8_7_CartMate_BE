package com.cartmate.cartmate.boundedContext.order.domain.groupshared;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
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

    private Long paymentId;

    private LocalDateTime paidAt;


    @Version
    private Long version;

    private SharedGroupOrderParticipant(SharedGroupOrder sharedGroupOrder, Long memberId, long shareAmount) {
        if (shareAmount < 0) {
            throw new ServiceException(OrderErrorCode.ORDER_INVALID_SHARE_AMOUNT);
        }
        this.sharedGroupOrder = sharedGroupOrder;
        this.memberId = memberId;
        this.shareAmount = shareAmount;
        this.status = SharedGroupOrderParticipantStatus.UNPAID;
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

    public boolean isPaidWith(Long paymentId) {
        return isPaid() && paymentId != null && paymentId.equals(this.paymentId);
    }

    void markPaid(Long paymentId, LocalDateTime paidAt) {
        if (status != SharedGroupOrderParticipantStatus.UNPAID) {
            throw new ServiceException(OrderErrorCode.ORDER_ALREADY_PAID);
        }
        this.status = SharedGroupOrderParticipantStatus.PAID;
        this.paymentId = paymentId;
        this.paidAt = paidAt;
    }
}
