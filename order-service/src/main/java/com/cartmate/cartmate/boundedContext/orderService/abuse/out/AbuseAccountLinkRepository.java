package com.cartmate.cartmate.boundedContext.orderService.abuse.out;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseAccountLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AbuseAccountLinkRepository extends JpaRepository<AbuseAccountLink, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO abuse_account_link
                (member_low_id, member_high_id, identity_type, shared_context_count, confirmed_at)
            VALUES (LEAST(:memberA, :memberB), GREATEST(:memberA, :memberB), :identityType, :sharedContextCount, :confirmedAt)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void insertIfAbsent(@Param("memberA") Long memberA,
                        @Param("memberB") Long memberB,
                        @Param("identityType") String identityType,
                        @Param("sharedContextCount") int sharedContextCount,
                        @Param("confirmedAt") LocalDateTime confirmedAt);

    @Query("select l from AbuseAccountLink l where l.memberLowId = :memberId or l.memberHighId = :memberId order by l.id")
    List<AbuseAccountLink> findByMember(@Param("memberId") Long memberId);

    boolean existsByMemberLowIdAndMemberHighId(Long memberLowId, Long memberHighId);
}
