package com.cartmate.cartmate.boundedContext.support.app;

import com.cartmate.cartmate.boundedContext.support.domain.NoticeCategory;
import com.cartmate.cartmate.boundedContext.support.dto.NoticeDetailResponse;
import com.cartmate.cartmate.boundedContext.support.dto.NoticeSaveRequest;
import com.cartmate.cartmate.boundedContext.support.dto.NoticeSummaryResponse;
import com.cartmate.cartmate.global.page.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoticeFacade {

    private final NoticeUseCase noticeUseCase;

    @Transactional(readOnly = true)
    public PageResponse<NoticeSummaryResponse> search(NoticeCategory category, String keyword, Pageable pageable) {
        return noticeUseCase.search(category, keyword, pageable);
    }

    // 관리자 상세 (조회수 안 올림)
    @Transactional(readOnly = true)
    public NoticeDetailResponse get(Long id) {
        return noticeUseCase.get(id);
    }

    // 사용자 상세 (조회수 올림)
    @Transactional
    public NoticeDetailResponse view(Long id) {
        return noticeUseCase.view(id);
    }

    @Transactional
    public NoticeDetailResponse create(Long adminId, NoticeSaveRequest request) {
        return noticeUseCase.create(adminId, request);
    }

    @Transactional
    public NoticeDetailResponse update(Long id, NoticeSaveRequest request) {
        return noticeUseCase.update(id, request);
    }

    @Transactional
    public void delete(Long id) {
        noticeUseCase.delete(id);
    }
}