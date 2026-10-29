package com.cartmate.cartmate.boundedContext.abuse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(
        name = "abuse_signal",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_abuse_signal_source",
                columnNames = {"member_id", "signal_type", "source_key"}
        ),
        indexes = @Index(name = "idx_abuse_signal_member_type_occurred", columnList = "member_id, signal_type, occurred_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AbuseSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(40)")
    private AbuseSignalType signalType;

    @Column(nullable = false, length = 64)
    private String sourceKey;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private AbuseSignalStatus status;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(20)")
    private AbuseLevel level;

    private Integer totalScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<AbuseRuleResult> ruleResults;

    private LocalDateTime judgedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    static AbuseSignal received(Long memberId, AbuseSignalType signalType, String sourceKey, LocalDateTime occurredAt,
                                LocalDateTime receivedAt) {
        AbuseSignal signal = new AbuseSignal();
        signal.memberId = memberId;
        signal.signalType = signalType;
        signal.sourceKey = sourceKey;
        signal.occurredAt = occurredAt;
        signal.receivedAt = receivedAt;
        signal.status = AbuseSignalStatus.RECEIVED;
        signal.createdAt = receivedAt;
        signal.updatedAt = receivedAt;
        return signal;
    }

    public void judge(AbuseVerdict verdict, LocalDateTime judgedAt) {
        this.level = verdict.level();
        this.totalScore = verdict.totalScore();
        this.ruleResults = new ArrayList<>(verdict.ruleResults());
        this.judgedAt = judgedAt;
        this.status = AbuseSignalStatus.JUDGED;
        this.updatedAt = judgedAt;
    }
}
