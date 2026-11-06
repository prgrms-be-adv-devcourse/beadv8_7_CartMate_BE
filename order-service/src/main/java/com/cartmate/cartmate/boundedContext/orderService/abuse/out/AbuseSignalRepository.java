package com.cartmate.cartmate.boundedContext.orderService.abuse.out;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AbuseSignalRepository extends JpaRepository<AbuseSignal, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO abuse_signal
                (member_id, signal_type, source_key, occurred_at, context_key, received_at, status, attempt_count,
                 created_at, updated_at)
            VALUES
                (:memberId, :signalType, :sourceKey, :occurredAt, :contextKey, :receivedAt, :status, 0,
                 :receivedAt, :receivedAt)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void insertIfAbsent(@Param("memberId") Long memberId,
                        @Param("signalType") String signalType,
                        @Param("sourceKey") String sourceKey,
                        @Param("occurredAt") LocalDateTime occurredAt,
                        @Param("contextKey") String contextKey,
                        @Param("receivedAt") LocalDateTime receivedAt,
                        @Param("status") String status);

    @Modifying
    @Query(value = """
            UPDATE abuse_signal
            SET status = CASE WHEN attempt_count + 1 >= :maxAttempts THEN 'FAILED' ELSE status END,
                attempt_count = attempt_count + 1,
                last_error = :error,
                updated_at = :now
            WHERE member_id = :memberId AND status = 'RECEIVED'
            """, nativeQuery = true)
    int recordFailureOfReceived(@Param("memberId") Long memberId,
                                @Param("error") String error,
                                @Param("maxAttempts") int maxAttempts,
                                @Param("now") LocalDateTime now);

    long countByMemberIdAndStatusAndUpdatedAt(Long memberId, AbuseSignalStatus status, LocalDateTime updatedAt);

    List<AbuseSignal> findByMemberIdOrderByOccurredAtAsc(Long memberId);

    List<AbuseSignal> findByMemberIdAndStatusOrderByOccurredAtAscIdAsc(Long memberId, AbuseSignalStatus status);

    @Query("""
            select s.memberId from AbuseSignal s
            where s.status = :status and s.receivedAt <= :before
            group by s.memberId
            order by min(s.receivedAt)
            """)
    List<Long> findMemberIdsByStatusReceivedBefore(@Param("status") AbuseSignalStatus status,
                                                   @Param("before") LocalDateTime before,
                                                   Limit limit);

    @Query("""
            select count(s) from AbuseSignal s
            where s.memberId = :memberId and s.signalType = :signalType
              and s.occurredAt >= :from and s.occurredAt <= :to
            """)
    long countInWindow(@Param("memberId") Long memberId,
                       @Param("signalType") AbuseSignalType signalType,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to);

    @Query("""
            select distinct s.contextKey from AbuseSignal s
            where s.memberId = :memberId and s.status = :status and s.contextKey is not null
            """)
    List<String> findContextKeysByMemberIdAndStatus(@Param("memberId") Long memberId,
                                                    @Param("status") AbuseSignalStatus status);

    @Query(value = """
            SELECT b.member_id AS memberId, COUNT(DISTINCT b.context_key) AS sharedContexts
            FROM abuse_signal a
            JOIN abuse_signal b ON b.context_key = a.context_key AND b.member_id <> a.member_id
            WHERE a.member_id = :memberId AND a.context_key IS NOT NULL
              AND b.member_id IN (SELECT c.member_id FROM abuse_signal c WHERE c.context_key IN (:contextKeys))
            GROUP BY b.member_id
            HAVING COUNT(DISTINCT b.context_key) >= :minSharedContexts
            ORDER BY b.member_id
            LIMIT :maxCandidates
            """, nativeQuery = true)
    List<SharedContextCandidate> findLinkCandidates(@Param("memberId") Long memberId,
                                                    @Param("contextKeys") Collection<String> contextKeys,
                                                    @Param("minSharedContexts") int minSharedContexts,
                                                    @Param("maxCandidates") int maxCandidates);

    interface SharedContextCandidate {

        Long getMemberId();

        Long getSharedContexts();
    }
}
