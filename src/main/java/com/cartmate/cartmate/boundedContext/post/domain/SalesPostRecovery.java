package com.cartmate.cartmate.boundedContext.post.domain;

import com.cartmate.cartmate.boundedContext.product.domain.ProductUpdateStatus;
import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesPostRecovery extends BaseIdAndTime {
    @Column(nullable = false, unique = true)
    private Long salesPostId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private SalesPostUpdateStatus previousStatus;

    @Column(nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private SalesPostSnapshot snapshot;

    @Column(nullable = false)
    private int retryCount;

    public SalesPostRecovery(Long salesPostId, SalesPostUpdateStatus previousStatus, SalesPostSnapshot snapshot) {
        this.salesPostId = salesPostId;
        this.previousStatus = previousStatus;
        this.snapshot = snapshot;
        this.retryCount = 0;
    }

    public void increaseRetryCount() {
        this.retryCount++;
    }
}
