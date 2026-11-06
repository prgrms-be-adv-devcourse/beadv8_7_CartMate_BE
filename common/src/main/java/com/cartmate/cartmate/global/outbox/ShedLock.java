package com.cartmate.cartmate.global.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ShedLock이 쓰는 {@code shedlock} 테이블. 코드에서 다루지 않고 ddl-auto가 만들게 하려고 엔티티로만 둔다.
 * 컬럼 이름 · 타입은 ShedLock JDBC 제공자의 기본 스키마를 따른다.
 */
@Entity
@Table(name = "shedlock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ShedLock {

    @Id
    @Column(name = "name", length = 64, nullable = false)
    private String name;

    @Column(name = "lock_until", nullable = false)
    private LocalDateTime lockUntil;

    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt;

    @Column(name = "locked_by", length = 255, nullable = false)
    private String lockedBy;
}
