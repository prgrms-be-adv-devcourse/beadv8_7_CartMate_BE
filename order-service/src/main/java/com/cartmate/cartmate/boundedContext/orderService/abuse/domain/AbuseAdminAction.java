package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "abuse_admin_action",
        indexes = @Index(name = "idx_abuse_admin_action_member_created", columnList = "member_id, created_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AbuseAdminAction {

    public static final int REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private AbuseAdminActionType actionType;

    @Column(nullable = false)
    private Long adminMemberId;

    @Column(nullable = false, length = REASON_LENGTH)
    private String reason;

    private Long restrictionId;

    private Integer affectedSignalCount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AbuseAdminAction of(Long memberId, AbuseAdminActionType actionType, Long adminMemberId,
                                      String reason, Long restrictionId, Integer affectedSignalCount,
                                      LocalDateTime createdAt) {
        AbuseAdminAction action = new AbuseAdminAction();
        action.memberId = memberId;
        action.actionType = actionType;
        action.adminMemberId = adminMemberId;
        action.reason = reason;
        action.restrictionId = restrictionId;
        action.affectedSignalCount = affectedSignalCount;
        action.createdAt = createdAt;
        return action;
    }
}
