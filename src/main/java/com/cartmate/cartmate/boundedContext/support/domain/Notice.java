package com.cartmate.cartmate.boundedContext.support.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 서비스 공지 (고객센터). 관리자만 작성하고, 로그인 없이 누구나 본다.
// support 모듈은 고객센터 콘텐츠(공지, 이후 FAQ·1:1 문의)를 모아 두는 곳이다.
@Entity
@Table(name = "NOTICE", indexes = @Index(name = "idx_notice_pinned_id", columnList = "pinned, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseIdAndTime {

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private NoticeCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 목록 맨 위에 고정
    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false)
    private long viewCount;

    // 작성한 관리자 회원 ID
    @Column(nullable = false)
    private Long authorId;

    public Notice(NoticeCategory category, String title, String content, boolean pinned, Long authorId) {
        this.category = category;
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.authorId = authorId;
    }

    public void update(NoticeCategory category, String title, String content, boolean pinned) {
        this.category = category;
        this.title = title;
        this.content = content;
        this.pinned = pinned;
    }
}