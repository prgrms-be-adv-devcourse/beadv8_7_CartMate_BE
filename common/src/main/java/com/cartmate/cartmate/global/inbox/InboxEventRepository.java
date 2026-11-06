package com.cartmate.cartmate.global.inbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InboxEventRepository extends JpaRepository<InboxEvent, Long> {

    List<InboxEvent> findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            InboxEventStatus status, LocalDateTime now, Limit limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from InboxEvent e where e.id = :id")
    Optional<InboxEvent> findByIdForUpdate(@Param("id") Long id);
}
