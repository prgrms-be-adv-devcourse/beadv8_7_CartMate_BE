package com.cartmate.cartmate.boundedContext.abuse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "abuse_signal",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_abuse_signal_source",
                columnNames = {"member_id", "signal_type", "source_key"}
        )
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

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
