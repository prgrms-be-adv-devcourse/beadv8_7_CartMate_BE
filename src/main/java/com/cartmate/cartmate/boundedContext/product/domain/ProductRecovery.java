package com.cartmate.cartmate.boundedContext.product.domain;

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

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRecovery extends BaseIdAndTime {
    @Column(nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private ProductUpdateStatus previousStatus;

    @Column(nullable = false, columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private ProductSnapshot snapshot;

    @Column(nullable = false)
    private int retryCount;

    public ProductRecovery(Long productId, ProductUpdateStatus previousStatus, ProductSnapshot snapshot) {
        this.productId = productId;
        this.previousStatus = previousStatus;
        this.snapshot = snapshot;
        this.retryCount = 0;
    }

    public void increaseRetryCount() {
        this.retryCount++;
    }
}
