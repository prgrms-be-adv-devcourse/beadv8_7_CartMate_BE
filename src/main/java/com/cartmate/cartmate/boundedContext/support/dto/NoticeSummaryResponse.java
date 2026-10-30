package com.cartmate.cartmate.boundedContext.support.dto;

import com.cartmate.cartmate.boundedContext.support.domain.Notice;
import com.cartmate.cartmate.boundedContext.support.domain.NoticeCategory;

import java.time.LocalDateTime;

public record NoticeSummaryResponse(
        Long id,
        NoticeCategory category,
        String title,
        boolean pinned,
        long viewCount,
        LocalDateTime createdAt
) {
    public static NoticeSummaryResponse from(Notice notice) {
        return new NoticeSummaryResponse(notice.getId(), notice.getCategory(), notice.getTitle(),
                notice.isPinned(), notice.getViewCount(), notice.getCreateDate());
    }
}