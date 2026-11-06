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
        name = "abuse_member_identity",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_abuse_member_identity",
                columnNames = {"member_id", "identity_type", "identity_hash"}
        ),
        indexes = @Index(name = "idx_abuse_member_identity_hash", columnList = "identity_type, identity_hash")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AbuseMemberIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private AbuseIdentityType identityType;

    @Column(nullable = false, length = 64)
    private String identityHash;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
