package com.cartmate.cartmate.boundedContext.orderService.abuse.out;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseMemberIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AbuseMemberIdentityRepository extends JpaRepository<AbuseMemberIdentity, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO abuse_member_identity (member_id, identity_type, identity_hash, created_at)
            VALUES (:memberId, :identityType, :identityHash, :createdAt)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void insertIfAbsent(@Param("memberId") Long memberId,
                        @Param("identityType") String identityType,
                        @Param("identityHash") String identityHash,
                        @Param("createdAt") LocalDateTime createdAt);

    List<AbuseMemberIdentity> findByMemberId(Long memberId);

    @Query(value = """
            SELECT a.identity_type
            FROM abuse_member_identity a
            JOIN abuse_member_identity b ON b.identity_type = a.identity_type AND b.identity_hash = a.identity_hash
            WHERE a.member_id = :memberA AND b.member_id = :memberB
              AND (SELECT COUNT(DISTINCT c.member_id) FROM abuse_member_identity c
                   WHERE c.identity_type = a.identity_type AND c.identity_hash = a.identity_hash) <= :maxMembers
            ORDER BY a.identity_type DESC
            """, nativeQuery = true)
    List<String> findSharedIdentityTypes(@Param("memberA") Long memberA,
                                         @Param("memberB") Long memberB,
                                         @Param("maxMembers") int maxMembers);
}
