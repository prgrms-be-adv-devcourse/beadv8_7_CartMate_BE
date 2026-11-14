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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "abuse_account_link",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_abuse_account_link_pair",
                columnNames = {"member_low_id", "member_high_id"}
        ),
        indexes = @Index(name = "idx_abuse_account_link_high", columnList = "member_high_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AbuseAccountLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberLowId;

    @Column(nullable = false)
    private Long memberHighId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private AbuseIdentityType identityType;

    @Column(nullable = false)
    private int sharedContextCount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime confirmedAt;

    public Long otherThan(Long memberId) {
        return memberLowId.equals(memberId) ? memberHighId : memberLowId;
    }
}
