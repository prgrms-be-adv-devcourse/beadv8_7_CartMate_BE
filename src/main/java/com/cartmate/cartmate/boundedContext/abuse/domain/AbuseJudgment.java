package com.cartmate.cartmate.boundedContext.abuse.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(
        name = "abuse_judgment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_abuse_judgment_signal",
                columnNames = {"member_id", "signal_type", "source_key"}
        ),
        indexes = @Index(name = "idx_abuse_judgment_member_judged_at", columnList = "member_id, judged_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AbuseJudgment {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private AbuseLevel level;

    @Column(nullable = false)
    private int totalScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private List<AbuseRuleResult> ruleResults = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime judgedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private AbuseJudgment(AbuseSignalCommand signal, List<AbuseRuleResult> ruleResults, AbuseScoreThreshold scoreThreshold,
                          LocalDateTime judgedAt) {
        this.memberId = signal.memberId();
        this.signalType = signal.type();
        this.sourceKey = signal.sourceKey();
        this.ruleResults = new ArrayList<>(ruleResults);
        this.totalScore = ruleResults.stream().mapToInt(AbuseRuleResult::score).sum();
        AbuseLevel highestRuleLevel = ruleResults.stream()
                .map(AbuseRuleResult::level)
                .reduce(AbuseLevel.NONE, AbuseLevel::max);
        this.level = AbuseLevel.max(highestRuleLevel, scoreThreshold.levelOf(totalScore));
        this.judgedAt = judgedAt;
    }

    public static AbuseJudgment judge(AbuseSignalCommand signal, List<AbuseRuleResult> ruleResults,
                                      AbuseScoreThreshold scoreThreshold, LocalDateTime judgedAt) {
        return new AbuseJudgment(signal, ruleResults, scoreThreshold, judgedAt);
    }

    public boolean isNone() {
        return level == AbuseLevel.NONE;
    }

    public boolean requiresRestriction() {
        return level == AbuseLevel.RESTRICTED;
    }
}
