package com.cartmate.cartmate.boundedContext.abuse.out;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignal;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AbuseSignalRepository extends JpaRepository<AbuseSignal, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO abuse_signal
                (member_id, signal_type, source_key, occurred_at, received_at, status, created_at, updated_at)
            VALUES
                (:memberId, :signalType, :sourceKey, :occurredAt, :receivedAt, :status, :receivedAt, :receivedAt)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void insertIfAbsent(@Param("memberId") Long memberId,
                        @Param("signalType") String signalType,
                        @Param("sourceKey") String sourceKey,
                        @Param("occurredAt") LocalDateTime occurredAt,
                        @Param("receivedAt") LocalDateTime receivedAt,
                        @Param("status") String status);

    List<AbuseSignal> findByMemberIdOrderByOccurredAtAsc(Long memberId);

    List<AbuseSignal> findByMemberIdAndStatusOrderByOccurredAtAscIdAsc(Long memberId, AbuseSignalStatus status);

    @Query("""
            select count(s) from AbuseSignal s
            where s.memberId = :memberId and s.signalType = :signalType
              and s.occurredAt >= :from and s.occurredAt <= :to
            """)
    long countInWindow(@Param("memberId") Long memberId,
                       @Param("signalType") AbuseSignalType signalType,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to);
}
