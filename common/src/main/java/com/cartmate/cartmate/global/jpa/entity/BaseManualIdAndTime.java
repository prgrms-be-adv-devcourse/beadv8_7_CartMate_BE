package com.cartmate.cartmate.global.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@MappedSuperclass
@Getter
// id 직접 지정 엔티티용 (복제 테이블 등)
public abstract class BaseManualIdAndTime extends BaseEntity {

    @Id
    private Long id;   // 자동 생성 없음. 원본 ID를 그대로 넣음

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createDate;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime modifyDate;

    protected BaseManualIdAndTime() {}

    protected BaseManualIdAndTime(Long id, LocalDateTime createDate, LocalDateTime modifyDate) {
        this.id = id;
        this.createDate = createDate;
        this.modifyDate = modifyDate;
    }
}
