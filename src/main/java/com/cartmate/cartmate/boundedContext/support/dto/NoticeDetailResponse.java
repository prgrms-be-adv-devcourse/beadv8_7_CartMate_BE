package com.cartmate.cartmate.boundedContext.support.dto;

import com.cartmate.cartmate.boundedContext.support.domain.Notice;
import com.cartmate.cartmate.boundedContext.support.domain.NoticeCategory;

import java.time.LocalDateTime;

public record NoticeDetailResponse(
        Long id,
        NoticeCategory category,
        String title,
        String content,
        boolean pinned,
        long viewCount,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt
) {
    public static NoticeDetailResponse from(Notice notice) {
        return new NoticeDetailResponse(notice.getId(), notice.getCategory(), notice.getTitle(), notice.getContent(),
                notice.isPinned(), notice.getViewCount(), notice.getCreateDate(), notice.getModifyDate());
    }
}