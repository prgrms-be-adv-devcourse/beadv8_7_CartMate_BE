package com.cartmate.cartmate.boundedContext.post.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 관리자 숨김·해제 이력. 누가, 언제, 왜 했는지 남긴다.
@Entity
@Table(name = "POST_SALES_POST_SANCTION",
        indexes = @Index(name = "idx_sales_post_sanction_post", columnList = "salesPostId"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesPostSanction extends BaseIdAndTime {

    @Column(nullable = false)
    private Long salesPostId;

    @Column(nullable = false)
    private Long adminId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private SalesPostSanctionAction action;

    @Column(nullable = false, length = 500)
    private String reason;

    public SalesPostSanction(Long salesPostId, Long adminId, SalesPostSanctionAction action, String reason) {
        this.salesPostId = salesPostId;
        this.adminId = adminId;
        this.action = action;
        this.reason = reason;
    }
}
