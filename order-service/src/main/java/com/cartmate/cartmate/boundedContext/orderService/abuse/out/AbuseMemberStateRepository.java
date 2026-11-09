package com.cartmate.cartmate.boundedContext.orderService.abuse.out;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseMemberState;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AbuseMemberStateRepository extends JpaRepository<AbuseMemberState, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO abuse_member_state (member_id, current_level, created_at, updated_at)
            VALUES (:memberId, 'NONE', :now, :now)
            ON DUPLICATE KEY UPDATE member_id = member_id
            """, nativeQuery = true)
    void insertIfAbsent(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AbuseMemberState s where s.memberId = :memberId")
    Optional<AbuseMemberState> findForUpdate(@Param("memberId") Long memberId);

    List<AbuseMemberState> findByCurrentLevelInOrderByLastJudgedAtDesc(Collection<AbuseLevel> levels, Limit limit);
}
